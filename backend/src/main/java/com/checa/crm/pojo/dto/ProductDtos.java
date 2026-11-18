package com.checa.crm.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;

public final class ProductDtos {
 private ProductDtos() {}
 public record Query(Integer pageNum,Integer pageSize,String keyword,String departmentId,String status,String category,String customer,String productId) {}
 public record CategorySave(@JsonAlias("name") String name,@JsonAlias("parent") String parent,Integer sort,String path,String description,String status,String departmentId) {}
 /** 产品档案 + 首个 SKU 的合并提交（前端产品表单）；带 productId 时表示给已有产品追加一个规格。 */
 public record ProductSave(@JsonAlias("name") String name,String sku,String brand,String category,String applicableScenes,@JsonAlias("spec") String specification,String packaging,String conversion,String unit,String repurchaseCycle,String relatedProducts,String substituteProducts,java.util.List<String> salesTags,@JsonAlias("usage") String usageNote,BigDecimal price,BigDecimal stock,String status,String description,String departmentId,String productId,String imageUrl) {}
 /** 单独维护 SKU（一产品多规格时使用）。 */
 public record SkuSave(String sku,@JsonAlias("spec") String specification,String packaging,String conversion,String unit,String repurchaseCycle,BigDecimal price,BigDecimal stock,String status) {}
 public record PriceSave(String customer,String customerLevel,String product,String sku,String priceType,BigDecimal minQuantity,BigDecimal maxQuantity,BigDecimal price,BigDecimal minimumPrice,String startDate,String endDate,Integer priority,String status,String note,String departmentId) {}
 public record Ids(java.util.List<String> ids) {}
 public record StatusPatch(java.util.List<String> ids,String status) {}
}
