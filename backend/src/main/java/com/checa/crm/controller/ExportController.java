package com.checa.crm.resource;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.SysUser;
import com.checa.crm.security.OrgPermissionGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端导出：按数据范围/品类/筛选导出当前账号可见数据为 CSV，并写入审计日志（方案 5.7 导出安全 + 导出审计）。
 */
@RestController
@RequestMapping("/export")
public class ExportController {
    private final GenericResourceService generic;
    private final GenericRecordMapper mapper;
    private final OrgPermissionGuard guard;
    private final SysUserMapper users;

    public ExportController(GenericResourceService generic, GenericRecordMapper mapper, OrgPermissionGuard guard, SysUserMapper users) {
        this.generic = generic; this.mapper = mapper; this.guard = guard; this.users = users;
    }

    @GetMapping("/{key}")
    public ResponseEntity<byte[]> export(@RequestAttribute("userId") String actor,
                                         @PathVariable String key,
                                         @RequestParam Map<String, String> params) {
        ResourceDefinition def = ResourceRegistry.byKey(key);
        if (def == null) throw new com.checa.crm.exception.BusinessException(404, "导出资源不存在");
        String exportPermission = def.readOnly() ? "report:export" : def.listPermission().replaceFirst(":list$", ":export");
        guard.require(actor, exportPermission);

        List<Map<String, Object>> rows = generic.exportRows(actor, def, params);
        String csv = toCsv(rows);

        SysUser u = users.findById(actor);
        mapper.audit(actor, u == null ? null : u.getUsername(), u == null ? null : u.getDeptId(),
                def.key(), "-", "EXPORT", null, null, "SUCCESS", null);

        byte[] bytes = ("\ufeff" + csv).getBytes(StandardCharsets.UTF_8);
        String filename = (def.key() + "-" + java.time.LocalDate.now() + ".csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    /** 按首行字段顺序生成 CSV；数组拼接、对象转 JSON、null 置空。 */
    private static String toCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "";
        List<String> headers = rows.get(0).keySet().stream().toList();
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(ExportController::escape).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            List<String> cells = headers.stream().map(h -> escape(cell(row.get(h)))).toList();
            sb.append(String.join(",", cells)).append("\r\n");
        }
        return sb.toString();
    }

    private static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) return list.stream().map(ExportController::cell).filter(s -> !s.isEmpty()).reduce((a, b) -> a + "、" + b).orElse("");
        if (value instanceof Map<?, ?> || value.getClass().isArray()) return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(value).toString();
        return String.valueOf(value);
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
