package com.checa.crm.pojo.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProductEntities {
 private ProductEntities() {}
 public static class Category { public String categoryId,departmentId,parentId,categoryName,parentName,path,description,status; public Integer sortNum,productCount; public java.time.LocalDateTime createdTime; }
 /** 产品档案（SPU）：只描述产品本身，具体规格、价格、库存在 SKU 上。 */
 public static class Product { public String productId,departmentId,categoryId,categoryName,productName,brand,applicableScenes,imageUrl,salesTags,usageNote,relatedProducts,substituteProducts,status,description; public Integer skuCount; }
 /** SKU：一个产品档案下的一条规格。 */
 public static class Sku { public String skuId,productId,skuCode,spec,packaging,conversion,unit,repurchaseCycle,status; public BigDecimal standardPrice,stock; }
 /** 产品 + SKU 的平铺视图，供产品列表/详情/选品使用。 */
 public static class ProductSku { public String skuId,productId,departmentId,categoryId,categoryName,productName,brand,applicableScenes,imageUrl,salesTags,usageNote,relatedProducts,substituteProducts,productStatus,description,skuCode,spec,packaging,conversion,unit,repurchaseCycle,skuStatus; public BigDecimal standardPrice,stock; public Integer skuCount; public java.time.LocalDateTime createdTime; }
 public static class Customer { public String customerId,departmentId,customerName,customerLevel,status; }
 public static class Price { public String priceId,departmentId,customerId,customerName,productId,productName,skuId,sku,customerLevel,priceType,status,note; public BigDecimal minQuantity,maxQuantity,price,minimumPrice; public LocalDate startDate,endDate; public Integer priority; public java.time.LocalDateTime createdTime; }
}
