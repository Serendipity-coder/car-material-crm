package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.ProductDtos;
import com.checa.crm.pojo.vo.ProductVos;
import com.checa.crm.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/product-categories")
public class ProductCategoryController {
 private final ProductService service; public ProductCategoryController(ProductService service){this.service=service;}
 @GetMapping public ApiResponse<ProductVos.Page<ProductVos.Category>> list(@RequestAttribute("userId")String actor,@RequestParam(defaultValue="1")Integer pageNum,@RequestParam(defaultValue="1000")Integer pageSize,@RequestParam(required=false)String keyword,@RequestParam(required=false)String departmentId,@RequestParam(required=false)String status){return ApiResponse.ok(service.categories(actor,new ProductDtos.Query(pageNum,pageSize,keyword,departmentId,status,null,null,null)));}
 /** 分类树（按当前账号的品类授权范围裁剪）。 */
 @GetMapping("/tree") public ApiResponse<List<ProductVos.CategoryNode>> tree(@RequestAttribute("userId")String actor){return ApiResponse.ok(service.categoryTree(actor));}
 @GetMapping("/{id}") public ApiResponse<ProductVos.Category> detail(@RequestAttribute("userId")String actor,@PathVariable String id){return ApiResponse.ok(service.categoryDetail(actor,id));}
 @PostMapping public ApiResponse<ProductVos.Category> create(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.CategorySave body){return ApiResponse.ok(service.createCategory(actor,body));}
 @PutMapping("/{id}") public ApiResponse<ProductVos.Category> update(@RequestAttribute("userId")String actor,@PathVariable String id,@RequestBody ProductDtos.CategorySave body){return ApiResponse.ok(service.updateCategory(actor,id,body));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@RequestAttribute("userId")String actor,@PathVariable String id){service.deleteCategory(actor,id);return ApiResponse.ok();}
 @DeleteMapping public ApiResponse<Void> batchDelete(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.Ids body){service.deleteCategories(actor,body.ids());return ApiResponse.ok();}
 @PatchMapping("/status") public ApiResponse<Void> status(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.StatusPatch body){service.categoryStatus(actor,body.ids(),body.status());return ApiResponse.ok();}
}
