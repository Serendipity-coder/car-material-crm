package com.checa.crm.service;

import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.mapper.OrgDeptMapper;
import com.checa.crm.mapper.OrgRoleMapper;
import com.checa.crm.pojo.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统操作日志统一记录器：供员工/角色/部门/权限/品类/数据范围等系统管理类操作自动留痕。
 * 一处埋点、处处生效，与业务资源日志（audit_log）同表，操作人账号与部门自动从用户信息取。
 * 另负责日志查询时的「记录」列可读名称解析（业务记录取 payload 名称字段，系统记录取用户/角色/部门名）。
 */
@Service
public class AuditLogService {
    private final GenericRecordMapper mapper;
    private final SysUserMapper users;
    private final OrgDeptMapper depts;
    private final OrgRoleMapper roles;
    private final ObjectMapper json;

    public AuditLogService(GenericRecordMapper mapper, SysUserMapper users, OrgDeptMapper depts, OrgRoleMapper roles, ObjectMapper json) {
        this.mapper = mapper; this.users = users; this.depts = depts; this.roles = roles; this.json = json;
    }

    /** 记录一条系统操作日志；before/after 为改前改后摘要（可为 null）。 */
    public void record(String operatorId, String resourceKey, String recordId, String action, Object before, Object after) {
        try {
            SysUser u = users.findById(operatorId);
            mapper.audit(operatorId,
                    u == null ? null : u.getUsername(),
                    u == null ? null : u.getDeptId(),
                    resourceKey, recordId, action,
                    toJson(before), toJson(after), "SUCCESS", null);
        } catch (Exception ignored) {
            // 日志写入失败不影响业务主流程
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            // 兜底：保证是合法 JSON 字符串
            String raw = String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + raw + "\"";
        }
    }

    /** 各业务资源 payload 中用于展示的可读名称字段（按优先级取第一个非空）。 */
    private static final Map<String, String[]> NAME_FIELDS = Map.ofEntries(
            Map.entry("todos", new String[]{"title"}),
            Map.entry("leads", new String[]{"name", "company", "storeName"}),
            Map.entry("pool", new String[]{"name", "company", "storeName"}),
            Map.entry("customers", new String[]{"name", "company", "storeName"}),
            Map.entry("stores", new String[]{"storeName", "name"}),
            Map.entry("contacts", new String[]{"name", "customer"}),
            Map.entry("followups", new String[]{"customer", "outcome"}),
            Map.entry("opportunities", new String[]{"name", "customer"}),
            Map.entry("quotes", new String[]{"quoteNo", "customer"}),
            Map.entry("orders", new String[]{"orderNo", "customer"}),
            Map.entry("returns", new String[]{"returnNo", "orderNo", "customer"}),
            Map.entry("shipments", new String[]{"shipmentNo", "trackingNo", "orderNo", "customer"}),
            Map.entry("payments", new String[]{"receiptNo", "orderNo", "customer"}),
            Map.entry("receivables", new String[]{"invoiceNo", "orderNo", "customer"}),
            Map.entry("service-tickets", new String[]{"ticketNo", "customer"}),
            Map.entry("visits", new String[]{"title", "customer"})
    );

    /** 为审计行补充 recordLabel（可读名称），解析失败时回退为原始 recordId。 */
    public void enrich(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        Map<String, List<String>> biz = new HashMap<>();
        List<String> userIds = new ArrayList<>();
        List<String> roleIds = new ArrayList<>();
        List<String> deptIds = new ArrayList<>();
        for (var row : rows) {
            String key = str(row.get("resourceKey"));
            String rid = str(row.get("recordId"));
            if (key == null || rid == null || rid.isBlank()) continue;
            List<String> ids = splitIds(rid);
            if ("system-user".equals(key)) userIds.addAll(ids);
            else if ("system-role".equals(key)) roleIds.addAll(ids);
            else if ("system-dept".equals(key)) deptIds.addAll(ids);
            else biz.computeIfAbsent(key, k -> new ArrayList<>()).addAll(ids);
        }
        Map<String, String> label = new HashMap<>();
        if (!userIds.isEmpty()) collect(label, userIds, users.namesByIds(userIds));
        if (!roleIds.isEmpty()) collect(label, roleIds, roles.namesByIds(roleIds));
        if (!deptIds.isEmpty()) collect(label, deptIds, depts.namesByIds(deptIds));
        if (!biz.isEmpty()) {
            List<String> all = biz.values().stream().flatMap(List::stream).distinct().toList();
            for (var m : mapper.payloadsByIds(all)) {
                String id = str(m.get("id"));
                String key = str(m.get("resourceKey"));
                String payload = str(m.get("payload"));
                if (id == null) continue;
                label.put(id, extractName(key, payload));
            }
        }
        for (var row : rows) {
            String key = str(row.get("resourceKey"));
            String rid = str(row.get("recordId"));
            String resolved = resolveLabel(label, key, rid);
            row.put("recordLabel", resolved == null || resolved.isBlank() ? rid : resolved);
        }
    }

    private static void collect(Map<String, String> label, List<String> ids, List<Map<String, Object>> rows) {
        if (ids.isEmpty() || rows == null) return;
        for (var m : rows) {
            String id = str(m.get("id"));
            String name = str(m.get("name"));
            if (id != null) label.put(id, name);
        }
    }

    private static String resolveLabel(Map<String, String> label, String key, String rid) {
        if (key == null || rid == null) return rid;
        if ("system-user".equals(key)) return joinNames(label, splitIds(rid));
        List<String> ids = splitIds(rid);
        if (ids.size() == 1) {
            String v = label.get(ids.get(0));
            return v == null || v.isBlank() ? rid : v;
        }
        return joinNames(label, ids);
    }

    private static String joinNames(Map<String, String> label, List<String> ids) {
        List<String> parts = new ArrayList<>();
        for (String id : ids) {
            String v = label.get(id);
            parts.add(v == null || v.isBlank() ? id : v);
        }
        return String.join("、", parts);
    }

    private static List<String> splitIds(String rid) {
        List<String> out = new ArrayList<>();
        for (String part : rid.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    private String extractName(String resourceKey, String payload) {
        if (payload == null || payload.isBlank()) return null;
        String[] fields = NAME_FIELDS.get(resourceKey);
        try {
            Map<String, Object> map = json.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
            if (fields != null) {
                for (String f : fields) {
                    String v = str(map.get(f));
                    if (v != null && !v.isBlank()) return v;
                }
            }
            // 兜底：取常见名称字段
            for (String f : new String[]{"name", "title", "company", "customer", "storeName"}) {
                String v = str(map.get(f));
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) {
            // 解析失败回退原始 id
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
