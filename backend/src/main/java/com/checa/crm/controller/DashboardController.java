package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestAttribute("userId") String actor,
            @RequestParam(required = false) String departmentId) {
        return ApiResponse.ok(service.getOverview(actor, departmentId));
    }
}
