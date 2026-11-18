package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.security.OrgPermissionGuard;
import com.checa.crm.service.ConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统参数：公海完整联系方式开关、长期未跟进自动回收配置。
 */
@RestController
@RequestMapping("/system/configs")
public class SystemConfigController {
    private final ConfigService service;
    private final OrgPermissionGuard guard;

    public SystemConfigController(ConfigService service, OrgPermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> all(@RequestAttribute("userId") String actor) {
        guard.require(actor, "system:config:edit");
        return ApiResponse.ok(service.all());
    }

    @PutMapping
    public ApiResponse<Map<String, String>> save(@RequestAttribute("userId") String actor, @RequestBody Map<String, String> body) {
        guard.require(actor, "system:config:edit");
        service.save(actor, body);
        return ApiResponse.ok(service.all());
    }
}
