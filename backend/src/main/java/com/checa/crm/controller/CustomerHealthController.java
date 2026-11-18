package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.CustomerHealthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CustomerHealthController {
    private final CustomerHealthService service;

    public CustomerHealthController(CustomerHealthService service) {
        this.service = service;
    }

    @PostMapping("/customers/recalculate-health")
    public ApiResponse<Map<String, Object>> recalculate(@RequestAttribute("userId") String actor) {
        return ApiResponse.ok(service.evaluateAllCustomers(actor));
    }
}
