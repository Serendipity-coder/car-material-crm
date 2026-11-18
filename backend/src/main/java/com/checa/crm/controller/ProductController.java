package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.ProductDtos;
import com.checa.crm.pojo.vo.ProductVos;
import com.checa.crm.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {
 private final ProductService service; public ProductController(ProductService service){this.service=service;}
 @GetMapping public ApiResponse<ProductVos.Page<ProductVos.Product>> list(@RequestAttribute("userId")String actor,@RequestParam(defaultValue="1")Integer pageNum,@RequestParam(defaultValue="1000")Integer pageSize,@RequestParam(required=false)String keyword,@RequestParam(required=false)String departmentId,@RequestParam(required=false)String status,@RequestParam(required=false)String category,@RequestParam(required=false)String productId){return ApiResponse.ok(service.products(actor,new ProductDtos.Query(pageNum,pageSize,keyword,departmentId,status,category,null,productId)));}
 @GetMapping("/{id}") public ApiResponse<ProductVos.Product> detail(@RequestAttribute("userId")String actor,@PathVariable String id){return ApiResponse.ok(service.productDetail(actor,id));}
 @PostMapping public ApiResponse<ProductVos.Product> create(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.ProductSave body){return ApiResponse.ok(service.createProduct(actor,body));}
 @PutMapping("/{id}") public ApiResponse<ProductVos.Product> update(@RequestAttribute("userId")String actor,@PathVariable String id,@RequestBody ProductDtos.ProductSave body){return ApiResponse.ok(service.updateProduct(actor,id,body));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@RequestAttribute("userId")String actor,@PathVariable String id){service.deleteProduct(actor,id);return ApiResponse.ok();}
 @DeleteMapping public ApiResponse<Void> batchDelete(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.Ids body){service.deleteProducts(actor,body.ids());return ApiResponse.ok();}
 @PatchMapping("/status") public ApiResponse<Void> status(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.StatusPatch body){service.productStatus(actor,body.ids(),body.status());return ApiResponse.ok();}

 // ---- 一个产品档案下的多个规格（SKU） ----
 @GetMapping("/{id}/skus") public ApiResponse<List<ProductVos.Product>> skus(@RequestAttribute("userId")String actor,@PathVariable String id){return ApiResponse.ok(service.skus(actor,id));}
 @PostMapping("/{id}/skus") public ApiResponse<ProductVos.Product> createSku(@RequestAttribute("userId")String actor,@PathVariable String id,@RequestBody ProductDtos.SkuSave body){return ApiResponse.ok(service.createSku(actor,id,body));}
 @PutMapping("/{id}/skus/{skuId}") public ApiResponse<ProductVos.Product> updateSku(@RequestAttribute("userId")String actor,@PathVariable String id,@PathVariable String skuId,@RequestBody ProductDtos.SkuSave body){return ApiResponse.ok(service.updateSku(actor,id,skuId,body));}
 @DeleteMapping("/{id}/skus/{skuId}") public ApiResponse<Void> deleteSku(@RequestAttribute("userId")String actor,@PathVariable String id,@PathVariable String skuId){service.deleteSku(actor,id,skuId);return ApiResponse.ok();}
}
