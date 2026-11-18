package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.CustomerStoreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/customers/{customerId}/stores")
public class CustomerStoreController {
    private final CustomerStoreService service;

    public CustomerStoreController(CustomerStoreService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestAttribute("userId") String actor,
            @PathVariable String customerId) {
        return ApiResponse.ok(service.listStores(actor, customerId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute("userId") String actor,
            @PathVariable String customerId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createStore(actor, customerId, body));
    }

    @PutMapping("/{storeId}")
    public ApiResponse<Map<String, Object>> update(
            @RequestAttribute("userId") String actor,
            @PathVariable String customerId,
            @PathVariable String storeId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateStore(actor, customerId, storeId, body));
    }

    @DeleteMapping("/{storeId}")
    public ApiResponse<Void> delete(
            @RequestAttribute("userId") String actor,
            @PathVariable String customerId,
            @PathVariable String storeId) {
        service.deleteStore(actor, customerId, storeId);
        return ApiResponse.ok();
    }
}
