package com.checa.crm.pojo.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProductVos {
 private ProductVos() {}
 public record Page<T>(List<T> rows,long total) {}
 public record Category(String id,String departmentId,String name,String parent,Integer sort,String path,String description,Integer productCount,String status,String createdAt) {}
 public record CategoryNode(String id,String parentId,String name,Integer sort,String status,Integer productCount,List<CategoryNode> children) {}
 /**
  * 产品行：一条记录对应一个 SKU（id 即 SKU 主键），同一产品档案的多个 SKU 会返回多行，
  * 因此前端无需改动即可展示“一个产品多规格”。
  */
 public record Product(String id,String productId,String departmentId,String categoryId,String category,String name,String displayName,String sku,String brand,String applicableScenes,String imageUrl,String spec,String packaging,String conversion,String unit,String repurchaseCycle,String relatedProducts,String substituteProducts,List<String> salesTags,String usage,BigDecimal price,BigDecimal stock,String status,String description,Integer skuCount,String createdAt) {}
 public record Price(String id,String departmentId,String customerId,String customer,String productId,String product,String skuId,String sku,String customerLevel,String priceType,BigDecimal minQuantity,BigDecimal maxQuantity,BigDecimal price,BigDecimal minimumPrice,LocalDate startDate,LocalDate endDate,Integer priority,String status,String note,String createdAt) {}
 public record CustomerOption(String id,String name,String departmentId,String level,String status) {}
}
