package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.ProductMapper;
import com.checa.crm.pojo.dto.ProductDtos;
import com.checa.crm.pojo.entity.ProductEntities;
import com.checa.crm.pojo.vo.ProductVos;
import com.checa.crm.security.OrgDataScopeService;
import com.checa.crm.security.OrgPermissionGuard;
import com.checa.crm.security.ProductCategoryScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 产品模块：产品分类树、产品档案（SPU）、SKU、客户价格。
 * <p>产品可见范围按"角色↔品类授权"计算（方案 5.10.3），不再按部门隔离；
 * 客户价格同时受部门数据范围与品类范围约束。</p>
 */
@Service
public class ProductService {
 private final ProductMapper mapper; private final OrgPermissionGuard guard; private final OrgDataScopeService scope; private final ProductCategoryScopeService categories;
 public ProductService(ProductMapper mapper,OrgPermissionGuard guard,OrgDataScopeService scope,ProductCategoryScopeService categories){this.mapper=mapper;this.guard=guard;this.scope=scope;this.categories=categories;}

 // ---------------- 产品分类 ----------------
 public ProductVos.Page<ProductVos.Category> categories(String actor,ProductDtos.Query q){
  guard.require(actor,"product:category:list");
  var ids=categories.visible(actor);
  if(ids!=null&&ids.isEmpty())return emptyPage(q);
  var rows=mapper.categories(q.keyword(),moduleStatus(q.status(),"启用"),ids).stream().map(this::category).toList();
  return page(rows,q.pageNum(),q.pageSize());
 }
 public List<ProductVos.CategoryNode> categoryTree(String actor){
  guard.require(actor,"product:category:list");
  var ids=categories.visible(actor);
  List<ProductEntities.Category> all=ids!=null&&ids.isEmpty()?List.of():mapper.categories(null,null,ids);
  return tree(all,"0");
 }
 public ProductVos.Category categoryDetail(String actor,String id){
  guard.require(actor,"product:category:list");
  var x=mapper.category(id); if(x==null)throw new BusinessException(404,"产品分类不存在");
  checkCategoryRead(actor,x.categoryId);
  return category(x);
 }
 @Transactional public ProductVos.Category createCategory(String actor,ProductDtos.CategorySave x){
  guard.require(actor,"product:category:add");
  String parent=parentId(required(x.name(),"分类名称"),x.parent());
  var allowed=categories.authorized(actor);
  if(allowed!=null){
   if("0".equals(parent))throw new BusinessException(403,"无权创建一级产品分类");
   if(!allowed.contains(parent))throw new BusinessException(403,"无权在该分类下创建子分类");
  }
  if(mapper.sameCategory(parent,required(x.name(),"分类名称"),"")>0)throw new BusinessException("同级分类名称已存在");
  String id="cat_"+uuid();
  mapper.addCategory(id,writeDept(actor,x.departmentId()),parent,required(x.name(),"分类名称"),x.sort()==null?0:x.sort(),blank(x.path()),blank(x.description()),status(x.status()));
  return category(mapper.category(id));
 }
 @Transactional public ProductVos.Category updateCategory(String actor,String id,ProductDtos.CategorySave x){
  guard.require(actor,"product:category:edit");
  var old=mapper.category(id); if(old==null)throw new BusinessException("产品分类不存在");
  checkCategoryWrite(actor,old.categoryId);
  String parent=parentId(required(x.name(),"分类名称"),x.parent());
  if(id.equals(parent)||mapper.descendantCount(id,parent)>0)throw new BusinessException("上级分类不能选择自身或其下级分类");
  if("0".equals(parent)&&categories.authorized(actor)!=null)throw new BusinessException(403,"无权把分类调整为一级分类");
  if(mapper.sameCategory(parent,required(x.name(),"分类名称"),id)>0)throw new BusinessException("同级分类名称已存在");
  mapper.updateCategory(id,parent,required(x.name(),"分类名称"),x.sort()==null?0:x.sort(),blank(x.path()),blank(x.description()),status(x.status()));
  return category(mapper.category(id));
 }
 @Transactional public void deleteCategory(String actor,String id){
  guard.require(actor,"product:category:remove");
  var old=mapper.category(id); if(old==null)throw new BusinessException("产品分类不存在");
  checkCategoryWrite(actor,old.categoryId);
  if(mapper.childCategories(id)>0)throw new BusinessException("该分类存在下级分类，不能删除");
  if(old.productCount!=null&&old.productCount>0)throw new BusinessException("该分类下仍有产品，不能删除");
  mapper.deleteCategory(id);
 }
 @Transactional public void deleteCategories(String actor,List<String> ids){
  guard.require(actor,"product:category:remove"); if(ids==null||ids.isEmpty())return;
  for(String id:ids){
   var old=mapper.category(id); if(old==null)continue;
   checkCategoryWrite(actor,old.categoryId);
   if(mapper.childCategories(id)>0)throw new BusinessException("分类「"+old.categoryName+"」存在下级分类，不能删除");
   if(old.productCount!=null&&old.productCount>0)throw new BusinessException("分类「"+old.categoryName+"」下仍有产品，不能删除");
   mapper.deleteCategory(id);
  }
 }
 @Transactional public void categoryStatus(String actor,List<String> ids,String status){
  guard.require(actor,"product:category:edit"); if(ids==null||ids.isEmpty())return;
  String s=status(status);
  for(String id:ids){var old=mapper.category(id);if(old==null)continue;checkCategoryWrite(actor,old.categoryId);mapper.updateCategoryStatus(id,s);}
 }

 // ---------------- 产品档案 + SKU ----------------
 public ProductVos.Page<ProductVos.Product> products(String actor,ProductDtos.Query q){
  guard.require(actor,"product:product:list");
  var ids=categories.authorized(actor);
  if(ids!=null&&ids.isEmpty())return emptyPage(q);
  String deptFilter=scope.departments(actor)==null?blank(q.departmentId()):null;
  if("__all__".equals(deptFilter))deptFilter=null;
  var rows=mapper.productSkus(q.keyword(),moduleStatus(q.status(),"在售"),q.category(),ids,blank(q.productId()),deptFilter).stream().map(this::product).toList();
  return page(rows,q.pageNum(),q.pageSize());
 }
 public ProductVos.Product productDetail(String actor,String id){
  guard.require(actor,"product:product:list");
  var row=mapper.productSku(id);
  if(row==null)row=mapper.firstSkuOfProduct(id);
  if(row==null)throw new BusinessException(404,"产品不存在");
  checkCategoryRead(actor,row.categoryId);
  return product(row);
 }
 /** 一个产品档案下的全部规格。 */
 public List<ProductVos.Product> skus(String actor,String productId){
  guard.require(actor,"product:product:list");
  var p=mapper.productById(productId); if(p==null)throw new BusinessException(404,"产品档案不存在");
  checkCategoryRead(actor,p.categoryId);
  return mapper.skusOfProduct(productId).stream().map(this::product).toList();
 }
 /**
  * 新建产品：不带 productId 时创建产品档案 + 首个 SKU；带 productId 时给已有产品追加一个规格（一产品多规格）。
  */
 @Transactional public ProductVos.Product createProduct(String actor,ProductDtos.ProductSave x){
  guard.require(actor,"product:product:add");
  var cat=mustCategory(required(x.category(),"产品分类"));
  checkCategoryWrite(actor,cat.categoryId);
  String skuCode=required(x.sku(),"SKU编码");
  if(mapper.skuCount(skuCode,"")>0)throw new BusinessException("SKU编码已存在（含已删除SKU占用的编码），请更换");
  String productId=x.productId()==null||x.productId().isBlank()?null:x.productId().trim();
  if(productId==null){
   // 同名、同品类的产品已存在时，直接追加规格（前端表单没有 productId 字段也能实现一产品多规格）
   String name=required(x.name(),"产品名称");
   String existing=mapper.productIdByNameAndCategory(name,cat.categoryId);
   if(existing!=null)productId=existing;
  }
  if(productId!=null){
   var p=mapper.productById(productId);
   if(p==null)throw new BusinessException("产品档案不存在");
   checkCategoryWrite(actor,p.categoryId);
   if(!p.categoryId.equals(cat.categoryId))throw new BusinessException("SKU 必须归属其产品档案所在的产品分类");
  }else{
   productId="prod_"+uuid();
   mapper.addProduct(productId,writeDept(actor,x.departmentId()),cat.categoryId,required(x.name(),"产品名称"),
     blank(x.brand()),blank(x.applicableScenes()),blank(x.imageUrl()),join(x.salesTags()),blank(x.usageNote()),
     blank(x.relatedProducts()),blank(x.substituteProducts()),productStatus(x.status()),blank(x.description()));
  }
  String skuId="sku_"+uuid();
  mapper.addSku(skuId,productId,skuCode,blank(x.specification()),blank(x.packaging()),blank(x.conversion()),blank(x.unit()),blank(x.repurchaseCycle()),
    money(x.price()),number(x.stock()),productStatus(x.status()));
  rollupProductStatus(productId);
  return product(mapper.productSku(skuId));
 }
 /** 给已有产品追加一个规格。 */
 @Transactional public ProductVos.Product createSku(String actor,String productId,ProductDtos.SkuSave x){
  guard.require(actor,"product:product:add");
  var p=mapper.productById(productId); if(p==null)throw new BusinessException(404,"产品档案不存在");
  checkCategoryWrite(actor,p.categoryId);
  String skuCode=required(x.sku(),"SKU编码");
  if(mapper.skuCount(skuCode,"")>0)throw new BusinessException("SKU编码已存在（含已删除SKU占用的编码），请更换");
  String skuId="sku_"+uuid();
  mapper.addSku(skuId,productId,skuCode,blank(x.specification()),blank(x.packaging()),blank(x.conversion()),blank(x.unit()),blank(x.repurchaseCycle()),
    money(x.price()),number(x.stock()),productStatus(x.status()));
  rollupProductStatus(productId);
  return product(mapper.productSku(skuId));
 }
 @Transactional public ProductVos.Product updateProduct(String actor,String id,ProductDtos.ProductSave x){
  guard.require(actor,"product:product:edit");
  var sku=resolveSku(id);
  var p=mapper.productById(sku.productId); if(p==null)throw new BusinessException("产品档案不存在");
  checkCategoryWrite(actor,p.categoryId);
  var cat=mustCategory(required(x.category(),"产品分类"));
  checkCategoryWrite(actor,cat.categoryId);
  if(!cat.categoryId.equals(p.categoryId)){
   if(categories.authorized(actor)!=null)throw new BusinessException(403,"只有拥有全部品类权限的账号才能调整产品所属品类");
   mapper.updateProductCategory(p.productId,cat.categoryId);
  }
  String skuCode=required(x.sku(),"SKU编码");
  if(mapper.skuCount(skuCode,sku.skuId)>0)throw new BusinessException("SKU编码已存在（含已删除SKU占用的编码），请更换");
  String image=x.imageUrl()==null?blank(p.imageUrl):blank(x.imageUrl());
  mapper.updateProduct(p.productId,required(x.name(),"产品名称"),blank(x.brand()),blank(x.applicableScenes()),image,
    join(x.salesTags()),blank(x.usageNote()),blank(x.relatedProducts()),blank(x.substituteProducts()),blank(x.description()));
  mapper.updateSku(sku.skuId,skuCode,blank(x.specification()),blank(x.packaging()),blank(x.conversion()),blank(x.unit()),blank(x.repurchaseCycle()),
    money(x.price()),number(x.stock()),productStatus(x.status()));
  rollupProductStatus(p.productId);
  return product(mapper.productSku(sku.skuId));
 }
 @Transactional public ProductVos.Product updateSku(String actor,String productId,String skuId,ProductDtos.SkuSave x){
  guard.require(actor,"product:product:edit");
  var p=mapper.productById(productId); if(p==null)throw new BusinessException(404,"产品档案不存在");
  checkCategoryWrite(actor,p.categoryId);
  var sku=mapper.sku(skuId); if(sku==null||!productId.equals(sku.productId))throw new BusinessException(404,"SKU 不存在");
  String skuCode=required(x.sku(),"SKU编码");
  if(mapper.skuCount(skuCode,skuId)>0)throw new BusinessException("SKU编码已存在（含已删除SKU占用的编码），请更换");
  mapper.updateSku(skuId,skuCode,blank(x.specification()),blank(x.packaging()),blank(x.conversion()),blank(x.unit()),blank(x.repurchaseCycle()),
    money(x.price()),number(x.stock()),productStatus(x.status()));
  rollupProductStatus(productId);
  return product(mapper.productSku(skuId));
 }
 /** 删除产品行：id 为 SKU 主键时删除该规格，删除最后一个规格时产品档案一并下线。 */
 @Transactional public void deleteProduct(String actor,String id){
  guard.require(actor,"product:product:remove");
  var sku=mapper.sku(id);
  if(sku!=null){deleteOneSku(actor,sku);return;}
  var p=mapper.productById(id); if(p==null)throw new BusinessException(404,"产品不存在");
  checkCategoryWrite(actor,p.categoryId);
  for(var row:mapper.skusOfProduct(id)){var s=mapper.sku(row.skuId);if(s!=null)deleteOneSku(actor,s);}
 }
 @Transactional public void deleteSku(String actor,String productId,String skuId){
  guard.require(actor,"product:product:remove");
  var p=mapper.productById(productId); if(p==null)throw new BusinessException(404,"产品档案不存在");
  checkCategoryWrite(actor,p.categoryId);
  var sku=mapper.sku(skuId); if(sku==null||!productId.equals(sku.productId))throw new BusinessException(404,"SKU 不存在");
  deleteOneSku(actor,sku);
 }
 @Transactional public void deleteProducts(String actor,List<String> ids){
  guard.require(actor,"product:product:remove"); if(ids==null||ids.isEmpty())return;
  for(String id:ids){
   var sku=mapper.sku(id);
   if(sku!=null){deleteOneSku(actor,sku);continue;}
   var p=mapper.productById(id); if(p==null)continue;
   checkCategoryWrite(actor,p.categoryId);
   for(var row:mapper.skusOfProduct(id)){var s=mapper.sku(row.skuId);if(s!=null)deleteOneSku(actor,s);}
  }
 }
 @Transactional public void productStatus(String actor,List<String> ids,String status){
  guard.require(actor,"product:product:edit"); if(ids==null||ids.isEmpty())return;
  String s=productStatus(status);
  for(String id:ids){
   var sku=mapper.sku(id); if(sku==null)continue;
   var p=mapper.productById(sku.productId); if(p==null)continue;
   checkCategoryWrite(actor,p.categoryId);
   mapper.updateSkuStatus(id,s);
   rollupProductStatus(sku.productId);
  }
 }

 // ---------------- 客户价格 ----------------
 public ProductVos.Page<ProductVos.Price> prices(String actor,ProductDtos.Query q){
  guard.require(actor,"product:price:list");
  var categoryIds=categories.authorized(actor);
  if(categoryIds!=null&&categoryIds.isEmpty())return emptyPage(q);
  var rows=mapper.prices(q.keyword(),moduleStatus(q.status(),"启用"),q.customer(),scope.departments(actor),categoryIds).stream().map(this::price).map(x->maskPrice(actor,x)).toList();
  return page(rows,q.pageNum(),q.pageSize());
 }
 public ProductVos.Price priceDetail(String actor,String id){
  guard.require(actor,"product:price:list");
  var x=mapper.price(id); if(x==null)throw new BusinessException(404,"客户价格不存在");
  checkDeptRead(actor,x.departmentId); checkCategoryRead(actor,categoryOf(x.skuId));
  return maskPrice(actor,price(x));
 }
 @Transactional public ProductVos.Price createPrice(String actor,ProductDtos.PriceSave x){
  guard.require(actor,"product:price:add");
  String dept=writeDept(actor,x.departmentId());
  String customer=mapper.customerId(required(x.customer(),"客户"),dept);
  if(customer==null)throw new BusinessException("客户不存在或不属于当前数据范围");
  checkDeptWrite(actor,mapper.customerDepartment(customer));
  var sku=resolveSku(required(x.product(),"产品"),x.sku());
  checkCategoryWrite(actor,sku.categoryId);
  validatePrice(x);
  String id="price_"+uuid();
  mapper.addPrice(id,dept,customer,sku.productId,sku.skuId,blank(x.customerLevel()),required(x.priceType(),"价格类型"),number(x.minQuantity()),x.maxQuantity(),money(x.price()),x.minimumPrice(),required(x.startDate(),"生效日期"),blank(x.endDate()),x.priority()==null?0:x.priority(),status(x.status()),blank(x.note()));
  return maskPrice(actor,price(mapper.price(id)));
 }
 @Transactional public ProductVos.Price updatePrice(String actor,String id,ProductDtos.PriceSave x){
  guard.require(actor,"product:price:edit");
  var old=mapper.price(id); if(old==null)throw new BusinessException("客户价格不存在");
  checkDeptWrite(actor,old.departmentId);
  String customer=mapper.customerId(required(x.customer(),"客户"),old.departmentId);
  if(customer==null)throw new BusinessException("客户不存在或不属于当前数据范围");
  checkDeptWrite(actor,mapper.customerDepartment(customer));
  var sku=resolveSku(required(x.product(),"产品"),x.sku());
  checkCategoryWrite(actor,sku.categoryId);
  validatePrice(x);
  mapper.updatePrice(id,customer,sku.productId,sku.skuId,blank(x.customerLevel()),required(x.priceType(),"价格类型"),number(x.minQuantity()),x.maxQuantity(),money(x.price()),x.minimumPrice(),required(x.startDate(),"生效日期"),blank(x.endDate()),x.priority()==null?0:x.priority(),status(x.status()),blank(x.note()));
  return maskPrice(actor,price(mapper.price(id)));
 }
 @Transactional public void deletePrice(String actor,String id){
  guard.require(actor,"product:price:remove");
  var old=mapper.price(id); if(old==null)throw new BusinessException("客户价格不存在");
  checkDeptWrite(actor,old.departmentId); checkCategoryWrite(actor,categoryOf(old.skuId));
  mapper.deletePrice(id);
 }
 @Transactional public void deletePrices(String actor,List<String> ids){
  guard.require(actor,"product:price:remove"); if(ids==null||ids.isEmpty())return;
  for(String id:ids){var old=mapper.price(id);if(old==null)continue;checkDeptWrite(actor,old.departmentId);checkCategoryWrite(actor,categoryOf(old.skuId));mapper.deletePrice(id);}
 }
 @Transactional public void priceStatus(String actor,List<String> ids,String status){
  guard.require(actor,"product:price:edit"); if(ids==null||ids.isEmpty())return;
  String s=status(status);
  for(String id:ids){var old=mapper.price(id);if(old==null)continue;checkDeptWrite(actor,old.departmentId);checkCategoryWrite(actor,categoryOf(old.skuId));mapper.updatePriceStatus(id,s);}
 }

 // ---------------- 内部工具 ----------------
 private void deleteOneSku(String actor,ProductEntities.Sku sku){
  var p=mapper.productById(sku.productId); if(p==null)return;
  checkCategoryWrite(actor,p.categoryId);
  if(mapper.priceCountBySku(sku.skuId)>0)throw new BusinessException("SKU「"+sku.skuCode+"」存在有效的客户价格，请先删除客户价格");
  mapper.deleteSku(sku.skuId);
  if(mapper.countSkusOfProduct(sku.productId)==0)mapper.deleteProduct(sku.productId);
 }
 private ProductEntities.Sku resolveSku(String id){
  var sku=mapper.sku(id);
  if(sku!=null)return sku;
  var first=mapper.firstSkuOfProduct(id);
  if(first==null)throw new BusinessException(404,"产品不存在");
  return mapper.sku(first.skuId);
 }
 /** 客户价格的目标 SKU：优先按 SKU 编码精确匹配，其次按产品名称取第一条规格。 */
 private ProductEntities.ProductSku resolveSku(String product,String skuCode){
  if(skuCode!=null&&!skuCode.isBlank()){
   var row=mapper.productSkuByCode(skuCode.trim());
   if(row==null)throw new BusinessException("SKU「"+skuCode+"」不存在或已下架");
   return row;
  }
  var row=mapper.productSkuByName(product);
  if(row==null)throw new BusinessException("产品「"+product+"」不存在或已下架");
  return row;
 }
 private String categoryOf(String skuId){
  if(skuId==null)return null;
  var sku=mapper.sku(skuId);
  if(sku==null)return null;
  var p=mapper.productById(sku.productId);
  return p==null?null:p.categoryId;
 }
 private ProductEntities.Category mustCategory(String name){
  String id=mapper.categoryIdByName(name);
  if(id==null)throw new BusinessException("产品分类不存在：" + name);
  var c=mapper.category(id);
  if(c==null)throw new BusinessException("产品分类不存在：" + name);
  return c;
 }
 private String parentId(String name,String parent){
  if(parent==null||parent.isBlank()||"—".equals(parent)||"0".equals(parent))return "0";
  String id=mapper.categoryIdByName(parent);
  if(id==null)throw new BusinessException("上级分类不存在");
  return id;
 }
 private void rollupProductStatus(String productId){
  int total=mapper.countSkusOfProduct(productId);
  int active=mapper.countSkusOfProductByStatus(productId,"在售");
  mapper.updateProductStatus(productId,total==0?"下架":(active>0?"在售":"下架"));
 }
 private void checkCategoryRead(String actor,String categoryId){var ids=categories.visible(actor);if(ids!=null&&(categoryId==null||!ids.contains(categoryId)))throw new BusinessException(403,"无权查看该产品品类");}
 private void checkCategoryWrite(String actor,String categoryId){var ids=categories.authorized(actor);if(ids!=null&&(categoryId==null||!ids.contains(categoryId)))throw new BusinessException(403,"无权操作该产品品类");}
 private String writeDept(String actor,String requested){var allowed=scope.departments(actor);if(allowed==null){if(requested==null||requested.isBlank())throw new BusinessException("请选择所属部门");return requested;}return allowed.get(0);}
 private void checkDeptWrite(String actor,String dept){var allowed=scope.departments(actor);if(allowed!=null&&(dept==null||!allowed.contains(dept)))throw new BusinessException("无权操作其他部门数据");}
 private void checkDeptRead(String actor,String dept){var allowed=scope.departments(actor);if(allowed!=null&&(dept==null||!allowed.contains(dept)))throw new BusinessException("无权查看其他部门数据");}
 private static String dateText(java.time.LocalDateTime time){return time==null?null:time.toLocalDate().toString();}
 private ProductVos.Category category(ProductEntities.Category x){return new ProductVos.Category(x.categoryId,x.departmentId,x.categoryName,"0".equals(x.parentId)?"—":(x.parentName==null?x.parentId:x.parentName),x.sortNum,x.path,x.description,x.productCount,statusText(x.status),dateText(x.createdTime));}
 private ProductVos.Product product(ProductEntities.ProductSku x){
  String display=x.spec==null||x.spec.isBlank()?x.productName:x.productName+" "+x.spec;
  return new ProductVos.Product(x.skuId,x.productId,x.departmentId,x.categoryId,x.categoryName,x.productName,display,x.skuCode,x.brand,x.applicableScenes,x.imageUrl,x.spec,x.packaging,x.conversion,x.unit,x.repurchaseCycle,x.relatedProducts,x.substituteProducts,split(x.salesTags),x.usageNote,x.standardPrice,x.stock,productStatusText(x.skuStatus),x.description,x.skuCount,dateText(x.createdTime));
 }
 /** 最低成交价/成本/毛利等敏感价格字段：仅管理员与授权角色可见，其余打码。 */
 private ProductVos.Price maskPrice(String actor,ProductVos.Price p){
  if(guard.has(actor,"system:data:all")||guard.has(actor,"crm:price:sensitive"))return p;
  return new ProductVos.Price(p.id(),p.departmentId(),p.customerId(),p.customer(),p.productId(),p.product(),p.skuId(),p.sku(),p.customerLevel(),p.priceType(),p.minQuantity(),p.maxQuantity(),p.price(),null,p.startDate(),p.endDate(),p.priority(),p.status(),p.note(),p.createdAt());
 }
 private ProductVos.Price price(ProductEntities.Price x){return new ProductVos.Price(x.priceId,x.departmentId,x.customerId,x.customerName,x.productId,x.productName,x.skuId,x.sku,x.customerLevel,x.priceType,x.minQuantity,x.maxQuantity,x.price,x.minimumPrice,x.startDate,x.endDate,x.priority,statusText(x.status),x.note,dateText(x.createdTime));}
 private List<ProductVos.CategoryNode> tree(List<ProductEntities.Category> all,String parent){
  return all.stream().filter(x->parent.equals(x.parentId==null?"0":x.parentId)).map(x->new ProductVos.CategoryNode(x.categoryId,x.parentId,x.categoryName,x.sortNum,statusText(x.status),x.productCount,tree(all,x.categoryId))).toList();
 }
 private <T> ProductVos.Page<T> page(List<T> rows,Integer pageNum,Integer pageSize){int page=Math.max(1,pageNum==null?1:pageNum),size=Math.min(1000,Math.max(1,pageSize==null?10:pageSize)),from=Math.min((page-1)*size,rows.size());return new ProductVos.Page<>(rows.subList(from,Math.min(from+size,rows.size())),rows.size());}
 private <T> ProductVos.Page<T> emptyPage(ProductDtos.Query q){return new ProductVos.Page<>(List.of(),0);}
 private void validatePrice(ProductDtos.PriceSave x){if(x.price()==null||x.price().compareTo(BigDecimal.ZERO)<=0)throw new BusinessException("客户单价必须大于0");if(x.minimumPrice()!=null&&x.price().compareTo(x.minimumPrice())<0)throw new BusinessException("客户单价不能低于最低成交价");if(x.maxQuantity()!=null&&x.minQuantity()!=null&&x.maxQuantity().compareTo(x.minQuantity())<0)throw new BusinessException("数量上限不能小于起订数量");}
 private static String uuid(){return UUID.randomUUID().toString().replace("-","");}
 private static String moduleStatus(String s,String active){if(s==null||s.isBlank())return null;if("进行中".equals(s)||"启用中".equals(s))return active;return s;}
 private static String required(String v,String n){if(v==null||v.isBlank())throw new BusinessException(n+"不能为空");return v.trim();}
 private static String blank(String v){return v==null?"":v;}
 private static String status(String v){return v==null||v.isBlank()?"进行中":v;}
 private static String productStatus(String v){return v==null||v.isBlank()?"在售":v;}
 /** 产品/SKU 状态：保持前端「在售/下架/草稿」口径，仅把历史「启用」映射为「在售」。 */
 private static String productStatusText(String v){
  if(v==null||v.isBlank())return "在售";
  return "启用".equals(v)||"启用中".equals(v)||"进行中".equals(v)?"在售":v;
 }
 private static String statusText(String v){
  if(v==null||v.isBlank())return "进行中";
  // 历史数据里的「启用/启用中/停用」统一成前端口径，避免下拉、筛选与列表对不上
  if("启用".equals(v)||"启用中".equals(v)||"在售".equals(v))return "进行中";
  if("停用".equals(v))return "已暂停";
  return v;
 }
 private static BigDecimal money(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
 private static BigDecimal number(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
 private static String join(List<String> x){return x==null?"":String.join(",",x);}
 private static List<String> split(String x){return x==null||x.isBlank()?List.of():Arrays.stream(x.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();}
}
