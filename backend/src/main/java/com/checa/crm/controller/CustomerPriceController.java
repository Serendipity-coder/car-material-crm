package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.ProductDtos;
import com.checa.crm.pojo.vo.ProductVos;
import com.checa.crm.service.ProductService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer-prices")
public class CustomerPriceController {
 private final ProductService service; public CustomerPriceController(ProductService service){this.service=service;}
 @GetMapping public ApiResponse<ProductVos.Page<ProductVos.Price>> list(@RequestAttribute("userId")String actor,@RequestParam(defaultValue="1")Integer pageNum,@RequestParam(defaultValue="1000")Integer pageSize,@RequestParam(required=false)String keyword,@RequestParam(required=false)String departmentId,@RequestParam(required=false)String status,@RequestParam(required=false)String customer){return ApiResponse.ok(service.prices(actor,new ProductDtos.Query(pageNum,pageSize,keyword,departmentId,status,null,customer,null)));}
 @GetMapping("/{id}") public ApiResponse<ProductVos.Price> detail(@RequestAttribute("userId")String actor,@PathVariable String id){return ApiResponse.ok(service.priceDetail(actor,id));}
 @PostMapping public ApiResponse<ProductVos.Price> create(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.PriceSave body){return ApiResponse.ok(service.createPrice(actor,body));}
 @PutMapping("/{id}") public ApiResponse<ProductVos.Price> update(@RequestAttribute("userId")String actor,@PathVariable String id,@RequestBody ProductDtos.PriceSave body){return ApiResponse.ok(service.updatePrice(actor,id,body));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@RequestAttribute("userId")String actor,@PathVariable String id){service.deletePrice(actor,id);return ApiResponse.ok();}
 @DeleteMapping public ApiResponse<Void> batchDelete(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.Ids body){service.deletePrices(actor,body.ids());return ApiResponse.ok();}
 @PatchMapping("/status") public ApiResponse<Void> status(@RequestAttribute("userId")String actor,@RequestBody ProductDtos.StatusPatch body){service.priceStatus(actor,body.ids(),body.status());return ApiResponse.ok();}
}
