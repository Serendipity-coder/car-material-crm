package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.LeadDuplicateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LeadController {
    private final LeadDuplicateService service;

    public LeadController(LeadDuplicateService service) {
        this.service = service;
    }

    @GetMapping("/leads/check-duplicate")
    public ApiResponse<Map<String, Object>> checkDuplicate(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String excludeId) {
        return ApiResponse.ok(service.checkDuplicate(phone, name, excludeId));
    }
}
