package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.ProductRecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProductRecommendationController {
    private final ProductRecommendationService service;

    public ProductRecommendationController(ProductRecommendationService service) {
        this.service = service;
    }

    @GetMapping("/products/recommendations")
    public ApiResponse<Map<String, Object>> recommendations(
            @RequestAttribute("userId") String actor,
            @RequestParam String customerId) {
        return ApiResponse.ok(service.getRecommendations(actor, customerId));
    }
}
