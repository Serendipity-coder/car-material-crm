package com.checa.crm.resource;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.ProductMapper;
import com.checa.crm.pojo.entity.ProductEntities;
import com.checa.crm.security.ProductCategoryScopeService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报价 / 订单 / 退换货 / 发货明细的 SKU 校验（方案 5.10.3：品类权限必须校验到 SKU）。
 * <p>前端字段是配置驱动的，明细行只有 product（产品名称）与 sku（编码）两个标识，
 * 这里逐行解析到 SKU，再校验该 SKU 所属品类是否在当前用户的授权范围内，
 * 避免只靠前端隐藏产品而被直接调用接口越权选品。</p>
 */
@Component
public class ProductSkuGuard {
    private static final Set<String> LINE_ITEM_RESOURCES = Set.of("quotes", "orders", "returns", "shipments");

    private final ProductMapper products;
    private final ProductCategoryScopeService categories;

    public ProductSkuGuard(ProductMapper products, ProductCategoryScopeService categories) {
        this.products = products;
        this.categories = categories;
    }

    public void validate(String actor, ResourceDefinition def, Map<String, Object> payload) {
        if (def == null || !LINE_ITEM_RESOURCES.contains(def.key())) return;
        List<?> items = payload.get("items") instanceof List<?> list ? list : List.of();
        boolean required = "quotes".equals(def.key()) || "orders".equals(def.key());
        if (items.isEmpty()) {
            if (required) throw new BusinessException("请至少添加一条产品明细");
            return;
        }
        var allowed = categories.authorized(actor);
        int index = 0;
        for (Object raw : items) {
            index++;
            if (!(raw instanceof Map<?, ?> item)) throw new BusinessException("第" + index + "行产品明细格式不正确");
            @SuppressWarnings("unchecked") Map<String, Object> mutable = (Map<String, Object>) item;
            String skuCode = text(item.get("sku"));
            String name = text(item.get("product"));
            ProductEntities.ProductSku row = null;
            if (skuCode != null) row = products.productSkuByCode(skuCode);
            if (row == null && name != null) row = products.productSkuByName(name);
            if (row == null) {
                String label = name != null ? name : skuCode;
                throw new BusinessException("第" + index + "行产品" + (label == null ? "" : "「" + label + "」") + "不存在，请从产品档案中选择");
            }
            // 明细只传产品名时，把解析到的 SKU 编码回写，保证报价/订单保存 SKU 快照
            if (skuCode == null) mutable.put("sku", row.skuCode);
            if (allowed != null && !allowed.contains(row.categoryId)) {
                throw new BusinessException(403, "第" + index + "行产品「" + row.productName + "」不在你被授权的产品品类范围内");
            }
            BigDecimal quantity = decimal(item.get("quantity"));
            if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("第" + index + "行数量必须大于 0");
            BigDecimal unitPrice = decimal(item.get("unitPrice"));
            if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("第" + index + "行单价不能为负数");
        }
    }

    private static String text(Object value) {
        if (value == null) return null;
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        try {
            String v = String.valueOf(value).trim();
            return v.isEmpty() ? null : new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
