package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.security.OrgPermissionGuard;
import com.checa.crm.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 操作日志查询（方案 8.5 / 5.x：角色分配、品类授权、数据变更、导出等操作留痕，提供查询接口）。
 */
@RestController
@RequestMapping("/audit-logs")
public class AuditController {
    private final GenericRecordMapper mapper;
    private final OrgPermissionGuard guard;
    private final AuditLogService auditLog;

    public AuditController(GenericRecordMapper mapper, OrgPermissionGuard guard, AuditLogService auditLog) {
        this.mapper = mapper; this.guard = guard; this.auditLog = auditLog;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestAttribute("userId") String actor,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 @RequestParam(required = false) String account,
                                                 @RequestParam(required = false) String resource,
                                                 @RequestParam(required = false) String action,
                                                 @RequestParam(required = false) String start,
                                                 @RequestParam(required = false) String end) {
        guard.require(actor, "system:audit:list");
        int size = Math.min(200, Math.max(1, pageSize));
        int offset = Math.max(0, pageNum - 1) * size;
        long total = mapper.auditCount(blank(account), blank(resource), blank(action), blank(start), blank(end));
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.auditPage(blank(account), blank(resource), blank(action), blank(start), blank(end), offset, size);
        auditLog.enrich(rows);
        return ApiResponse.ok(Map.of("rows", rows, "total", total));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
