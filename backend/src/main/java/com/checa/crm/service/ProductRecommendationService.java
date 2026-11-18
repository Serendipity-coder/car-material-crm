package com.checa.crm.service;

import com.checa.crm.mapper.ProductMapper;
import com.checa.crm.pojo.entity.ProductEntities;
import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.security.ProductCategoryScopeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 智能产品推荐引擎（方案 6.7 & 10.2）：
 * 基于汽修汽保耗材渠道采购历史，提供：
 * 1. 沉睡品唤醒（历史采购过但超 60 天未复购）
 * 2. 耗材临期周期预测（按 SKU 预计复购周期预测补货）
 * 3. 交叉销售关联推荐（机油配机滤/清洗剂，洗车液配镀膜水蜡）
 * 4. 品质升级推荐（半合成升级全合成机油）
 */
@Service
public class ProductRecommendationService {
    private final GenericRecordMapper mapper;
    private final ProductMapper products;
    private final ProductCategoryScopeService categoryScope;
    private final ObjectMapper json;

    public ProductRecommendationService(GenericRecordMapper mapper, ProductMapper products, ProductCategoryScopeService categoryScope, ObjectMapper json) {
        this.mapper = mapper;
        this.products = products;
        this.categoryScope = categoryScope;
        this.json = json;
    }

    public Map<String, Object> getRecommendations(String actor, String customerId) {
        LocalDate today = LocalDate.now();

        // 1. 校验品类权限
        List<String> allowedCategories = categoryScope.authorized(actor);

        // 2. 获取该客户历史订单明细
        List<Map<String, Object>> orders = mapper.listByCustomerId("orders", customerId);
        Map<String, LocalDate> lastPurchasedSkus = new HashMap<>();
        Set<String> purchasedProductNames = new HashSet<>();
        Set<String> purchasedCategoryNames = new HashSet<>();

        for (var ordRow : orders) {
            Map<String, Object> ord = readPayload(ordRow);
            String ordDateStr = text(ord.get("orderDate"));
            if (ordDateStr == null) ordDateStr = text(ord.get("createdAt"));
            LocalDate ordDate = parseDate(ordDateStr);

            if (ord.get("items") instanceof List<?> list) {
                for (Object itemObj : list) {
                    if (itemObj instanceof Map<?, ?> item) {
                        String sku = text(item.get("sku"));
                        String pName = text(item.get("product"));
                        if (pName != null) purchasedProductNames.add(pName);
                        if (sku != null && ordDate != null) {
                            if (!lastPurchasedSkus.containsKey(sku) || ordDate.isAfter(lastPurchasedSkus.get(sku))) {
                                lastPurchasedSkus.put(sku, ordDate);
                            }
                        }
                    }
                }
            }
        }

        // 3. 查询可用 SKU 列表
        List<ProductEntities.ProductSku> allSkus = products.productSkus(null, "在售", null, allowedCategories, null, null);
        Map<String, ProductEntities.ProductSku> skuMap = new HashMap<>();
        for (var s : allSkus) {
            skuMap.put(s.skuCode, s);
            if (purchasedProductNames.contains(s.productName)) {
                purchasedCategoryNames.add(s.categoryName);
            }
        }

        List<Map<String, Object>> dormantRecommendations = new ArrayList<>();
        List<Map<String, Object>> cycleRecommendations = new ArrayList<>();
        List<Map<String, Object>> crossSellRecommendations = new ArrayList<>();
        List<Map<String, Object>> upsellRecommendations = new ArrayList<>();

        // (1) 沉睡品唤醒 & 周期预测
        for (var entry : lastPurchasedSkus.entrySet()) {
            String skuCode = entry.getKey();
            LocalDate lastDate = entry.getValue();
            ProductEntities.ProductSku sku = skuMap.get(skuCode);
            if (sku == null) continue;

            long daysSince = ChronoUnit.DAYS.between(lastDate, today);

            // 复购周期预测
            int cycleDays = parseCycleDays(sku.repurchaseCycle);
            if (cycleDays > 0) {
                long remaining = cycleDays - daysSince;
                if (remaining <= 10) {
                    cycleRecommendations.add(buildItem(sku, "预计补货临期",
                            "该耗材预计复购周期为 " + sku.repurchaseCycle + "，上次采购于 " + lastDate + "（已距 " + daysSince + " 天），预计耗材已临近用尽"));
                }
            }

            // 沉睡品唤醒
            if (daysSince >= 60) {
                dormantRecommendations.add(buildItem(sku, "沉睡耗材唤醒",
                        "客户历史采购过此产品，但已超 " + daysSince + " 天未再次下单，建议提醒客户查库存补货"));
            }
        }

        // (2) 交叉关联销售推荐
        for (var sku : allSkus) {
            if (lastPurchasedSkus.containsKey(sku.skuCode)) continue; // 已经常常买的不再交叉推荐
            String name = sku.productName.toLowerCase();
            String cat = sku.categoryName;

            // 若客户采购过机油类，推荐滤清器或清洗油
            if (purchasedCategoryNames.stream().anyMatch(c -> c.contains("机油") || c.contains("润滑"))) {
                if (cat.contains("滤清") || name.contains("机滤") || name.contains("清洗剂") || name.contains("螺栓垫片")) {
                    crossSellRecommendations.add(buildItem(sku, "保养必备辅料", "客户采购过机油类润滑产品，推荐配套采购优质机油滤清器与清洗养护耗材"));
                }
            }
            // 若客户采购过洗车美容类，推荐镀膜水蜡或光亮剂
            if (purchasedCategoryNames.stream().anyMatch(c -> c.contains("洗车") || c.contains("美容"))) {
                if (name.contains("镀膜") || name.contains("光亮") || name.contains("毛巾") || name.contains("海绵")) {
                    crossSellRecommendations.add(buildItem(sku, "美容连带推荐", "客户采购过洗车液耗材，推荐搭配高毛利镀膜水蜡与专业施工物料"));
                }
            }
            // 若客户采购过制动/刹车类，推荐刹车油或刹车养护套装
            if (purchasedCategoryNames.stream().anyMatch(c -> c.contains("制动") || c.contains("刹车"))) {
                if (name.contains("养护") || name.contains("润滑脂") || name.contains("清洗")) {
                    crossSellRecommendations.add(buildItem(sku, "制动系统辅料", "配合刹车更换施工的标准养护辅料，增强作业安全性与客单价"));
                }
            }
        }

        // (3) 品质升级推荐
        for (var sku : allSkus) {
            String spec = sku.spec != null ? sku.spec : "";
            String name = sku.productName;
            if (spec.contains("全合成") || name.contains("全合成") || name.contains("高端") || name.contains("纳米水晶")) {
                if (!lastPurchasedSkus.containsKey(sku.skuCode)) {
                    upsellRecommendations.add(buildItem(sku, "品质升级推荐", "高端全合成/长效配方耗材，满足客户车主对高品质快保养护的需求"));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("repurchaseDue", cycleRecommendations.stream().limit(5).toList());
        result.put("dormantAwakening", dormantRecommendations.stream().limit(5).toList());
        result.put("crossSell", crossSellRecommendations.stream().limit(5).toList());
        result.put("upsell", upsellRecommendations.stream().limit(5).toList());
        result.put("totalRecommended", cycleRecommendations.size() + dormantRecommendations.size() + crossSellRecommendations.size() + upsellRecommendations.size());

        return result;
    }

    private Map<String, Object> buildItem(ProductEntities.ProductSku sku, String tag, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("skuId", sku.skuId);
        map.put("productId", sku.productId);
        map.put("skuCode", sku.skuCode);
        map.put("productName", sku.productName);
        map.put("displayName", sku.productName + (sku.spec != null && !sku.spec.isBlank() ? " (" + sku.spec + ")" : ""));
        map.put("categoryName", sku.categoryName);
        map.put("spec", sku.spec);
        map.put("unit", sku.unit);
        map.put("standardPrice", sku.standardPrice);
        map.put("repurchaseCycle", sku.repurchaseCycle);
        map.put("recommendTag", tag);
        map.put("recommendReason", reason);
        return map;
    }

    private int parseCycleDays(String cycle) {
        if (cycle == null || cycle.isBlank()) return 0;
        try {
            String digits = cycle.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            int num = Integer.parseInt(digits);
            if (cycle.contains("月")) return num * 30;
            if (cycle.contains("天") || cycle.contains("日")) return num;
            return num;
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, Object> readPayload(Map<String, Object> row) {
        try {
            if (row.get("payload") != null) {
                return json.readValue(String.valueOf(row.get("payload")), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    private static String text(Object val) {
        if (val == null) return null;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate parseDate(String val) {
        if (val == null) return null;
        try {
            return LocalDate.parse(val.length() >= 10 ? val.substring(0, 10) : val);
        } catch (Exception e) {
            return null;
        }
    }
}
