package com.checa.crm.resource;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.OrgDeptMapper;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.SysUser;
import com.checa.crm.security.DataScopeService;
import com.checa.crm.security.OrgDataScopeService;
import com.checa.crm.service.ConfigService;
import com.checa.crm.security.OrgPermissionGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 通用业务资源服务：18 个可写资源共用一套存储（crm_record.payload JSON）与一套数据范围/权限/审计逻辑。
 * 前端字段是配置驱动的，因此服务端按 payload 透传，只负责身份、权限、部门归属、审计与软删除。
 */
@Service
public class GenericResourceService {
    private static final Set<String> RESERVED = Set.of("pageNum", "pageSize", "keyword", "search", "sortBy", "sortOrder", "departmentId", "startDate", "endDate", "view");

    private final GenericRecordMapper mapper;
    private final OrgPermissionGuard guard;
    private final OrgDataScopeService scope;
    private final SysUserMapper users;
    private final OrgDeptMapper depts;
    private final ObjectMapper json;
    private final ProductSkuGuard skuGuard;
    private final DataScopeService dataScope;
    private final ReportService reportService;
    private final ConfigService config;
    private final ReceivableAgingService agingService;

    public GenericResourceService(GenericRecordMapper mapper, OrgPermissionGuard guard, OrgDataScopeService scope, SysUserMapper users, OrgDeptMapper depts, ObjectMapper json, ProductSkuGuard skuGuard, DataScopeService dataScope, ReportService reportService, ConfigService config, ReceivableAgingService agingService) {
        this.mapper = mapper; this.guard = guard; this.scope = scope; this.users = users; this.depts = depts; this.json = json; this.skuGuard = skuGuard;
        this.dataScope = dataScope; this.reportService = reportService; this.config = config; this.agingService = agingService;
    }

    public PageResult<Map<String, Object>> list(String actor, ResourceDefinition def, Map<String, String> params, String ip) {
        guard.require(actor, def.listPermission());
        // 报表在服务端完成日期区间过滤与聚合
        if (def.readOnly()) return reportService.page(actor, def, params);
        int page = Math.max(1, parseInt(params.get("pageNum"), 1));
        int size = Math.min(1000, Math.max(1, parseInt(params.get("pageSize"), 20)));
        String requestedDept = blankToNull(params.get("departmentId"));
        if ("__all__".equals(requestedDept)) requestedDept = null;
        var scope = resolveScope(actor, def);
        List<String> deptIds = null;
        String selfId = null, selfName = null;
        boolean poolOnly = false;
        if (def.ownerScoped()) {
            deptIds = null;
        } else if ("pool".equals(def.key())) {
            // 公海记录企业内共享：默认不按部门过滤，可选按部门收窄
            deptIds = requestedDept == null ? null : List.of(requestedDept);
        } else if (scope.all()) {
            deptIds = requestedDept == null ? null : List.of(requestedDept);
        } else if (scope.selfOnly()) {
            selfId = actor;
            selfName = scope.userName();
        } else {
            deptIds = scope.deptIds();
        }
        // 线索视图：mine 只看本人；pool/unassigned 只看未分配（企业内共享）
        String view = blankToNull(params.get("view"));
        if ("leads".equals(def.key()) && view != null) {
            switch (view) {
                case "mine" -> { deptIds = null; selfId = actor; selfName = realName(actor); }
                case "pool", "unassigned" -> { deptIds = null; selfId = null; selfName = null; poolOnly = true; }
                default -> { }
            }
        }
        String ownerName = def.ownerScoped() && !scope.all() ? realName(actor) : null;
        Map<String, String> filters = filters(params);
        String keyword = blankToNull(params.get("keyword")) != null ? params.get("keyword").trim() : blankToNull(params.get("search"));
        String orderBy = orderField(params.get("sortBy"));
        String direction = "asc".equalsIgnoreCase(params.get("sortOrder")) ? "asc" : "desc";
        int offset = (page - 1) * size;
        long total = mapper.count(def.key(), deptIds, actor, ownerName, selfId, selfName, poolOnly, keyword, filters);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.page(def.key(), deptIds, actor, ownerName, selfId, selfName, poolOnly, keyword, filters, orderBy, direction, offset, size).stream().map(this::toRecord).toList();
        if ("receivables".equals(def.key())) {
            for (var row : rows) agingService.recalculate(row);
        }
        // 公海（含线索公海视图）联系方式脱敏
        for (var row : rows) if (maskContactFor(actor, def, view, row)) maskContact(row);
        // 敏感价格字段（最低成交价/成本/毛利）脱敏
        for (var row : rows) maskSensitive(actor, row);
        return new PageResult<>(rows, total);
    }

    /** 导出用：返回当前账号可见的全部行（含报表聚合），供服务端 CSV 导出复用。 */
    public List<Map<String, Object>> exportRows(String actor, ResourceDefinition def, Map<String, String> params) {
        guard.require(actor, def.listPermission());
        if (def.readOnly()) return reportService.page(actor, def, params).rows();
        int size = 50000;
        String requestedDept = blankToNull(params.get("departmentId"));
        if ("__all__".equals(requestedDept)) requestedDept = null;
        var scope = resolveScope(actor, def);
        List<String> deptIds = null;
        String selfId = null, selfName = null;
        boolean poolOnly = false;
        if (def.ownerScoped()) deptIds = null;
        else if ("pool".equals(def.key())) deptIds = requestedDept == null ? null : List.of(requestedDept);
        else if (scope.all()) deptIds = requestedDept == null ? null : List.of(requestedDept);
        else if (scope.selfOnly()) { selfId = actor; selfName = scope.userName(); }
        else deptIds = scope.deptIds();
        String view = blankToNull(params.get("view"));
        if ("leads".equals(def.key()) && view != null) {
            switch (view) {
                case "mine" -> { deptIds = null; selfId = actor; selfName = realName(actor); }
                case "pool", "unassigned" -> { deptIds = null; selfId = null; selfName = null; poolOnly = true; }
                default -> { }
            }
        }
        String ownerName = def.ownerScoped() && !scope.all() ? realName(actor) : null;
        Map<String, String> filters = filters(params);
        String keyword = blankToNull(params.get("keyword")) != null ? params.get("keyword").trim() : blankToNull(params.get("search"));
        List<Map<String, Object>> rows = mapper.page(def.key(), deptIds, actor, ownerName, selfId, selfName, poolOnly, keyword, filters, null, "desc", 0, size).stream().map(this::toRecord).toList();
        if ("receivables".equals(def.key())) for (var row : rows) agingService.recalculate(row);
        for (var row : rows) if (maskContactFor(actor, def, view, row)) maskContact(row);
        for (var row : rows) maskSensitive(actor, row);
        return rows;
    }

    public Map<String, Object> get(String actor, ResourceDefinition def, String id) {
        guard.require(actor, def.listPermission());
        Map<String, Object> row = mustFind(def, id);
        assertWritable(actor, def, row);
        Map<String, Object> out = toRecord(row);
        if ("receivables".equals(def.key())) agingService.recalculate(out);
        if (maskContactFor(actor, def, null, out)) maskContact(out);
        maskSensitive(actor, out);
        return out;
    }

    @Transactional
    public Map<String, Object> create(String actor, ResourceDefinition def, Map<String, Object> body, String ip) {
        notReadOnly(def);
        guard.require(actor, def.addPermission());
        if ("pool".equals(def.key())) guard.require(actor, "crm:pool:assign");
        if (body == null) throw new BusinessException("请求内容不能为空");
        if ("leads".equals(def.key()) && !Boolean.TRUE.equals(body.get("ignoreDuplicate"))) {
            String phone = text(body.get("phone"));
            String name = text(body.get("name") != null ? body.get("name") : body.get("company"));
            checkLeadDuplicate(phone, name);
        }
        String dept = resolveWriteDept(actor, def, body.get("departmentId"));
        String id = def.idPrefix() + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("id", id);
        payload.put("departmentId", dept);
        if (payload.get("createdAt") == null || String.valueOf(payload.get("createdAt")).isBlank()) payload.put("createdAt", LocalDate.now().toString());
        String status = text(payload.get("status"));
        skuGuard.validate(actor, def, payload);
        mapper.insert(id, def.key(), write(payload), dept, ownerIdOf(payload.get("owner")), status, actor);
        mirror(def, payload, dept);
        audit(actor, def, id, "CREATE", null, payload, ip);
        return toRecord(mustFind(def, id));
    }

    @Transactional
    public Map<String, Object> update(String actor, ResourceDefinition def, String id, Map<String, Object> body, String ip) {
        notReadOnly(def);
        guard.require(actor, def.editPermission());
        Map<String, Object> row = mustFind(def, id);
        assertWritable(actor, def, row);
        Map<String, Object> before = toRecord(row);
        if ("pool".equals(def.key()) && body != null) {
            for (String field : List.of("owner", "status", "claimedAt")) {
                if (body.containsKey(field) && !Objects.equals(text(body.get(field)), text(before.get(field))))
                    throw new BusinessException("公海记录的负责人与状态请通过领取/分配/回收操作变更");
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>(before);
        if (body != null) {
            // 前端拿到的是脱敏值，提交时不能把带 * 的号码写回数据库
            for (String field : List.of("phone", "wechat")) {
                Object value = body.get(field);
                if (value != null && String.valueOf(value).contains("*")) body.remove(field);
            }
        }
        if (body != null) body.forEach((k, v) -> { if (v != null) payload.put(k, v); });
        payload.put("id", id);
        String dept = before.get("departmentId") == null ? null : String.valueOf(before.get("departmentId"));
        if (def.ownerScoped()) {
            dept = ownDept(actor);
        } else if (resolveScope(actor, def).all()) {
            String requested = text(body == null ? null : body.get("departmentId"));
            if (requested != null && depts.find(requested) != null) dept = requested;
            if (dept == null) throw new BusinessException("请选择所属部门");
        }
        payload.put("departmentId", dept);
        skuGuard.validate(actor, def, payload);
        mapper.update(id, write(payload), dept, ownerIdOf(payload.get("owner")), text(payload.get("status")), actor);
        mirror(def, payload, dept);
        audit(actor, def, id, "UPDATE", before, payload, ip);
        return toRecord(mustFind(def, id));
    }

    @Transactional
    public void delete(String actor, ResourceDefinition def, List<String> ids, String ip) {
        notReadOnly(def);
        guard.require(actor, def.removePermission());
        if ("pool".equals(def.key())) guard.require(actor, "crm:pool:assign");
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            Map<String, Object> row = mapper.find(id);
            if (row == null) continue;
            assertWritable(actor, def, row);
            if ("orders".equals(def.key())) {
                String st = text(row.get("metaStatus"));
                if (st == null) {
                    Map<String, Object> rec = toRecord(row);
                    st = text(rec.get("status"));
                }
                if ("已确认".equals(st) || "进行中".equals(st) || "已完成".equals(st) || "执行中".equals(st)) {
                    throw new BusinessException("已确认或执行中的订单禁止直接删除，请先调整订单状态或申请作废");
                }
            }
            mapper.softDelete(id, actor);
            if ("customers".equals(def.key())) mapper.removeCustomerMirror(id);
            audit(actor, def, id, "DELETE", toRecord(row), null, ip);
        }
    }

    @Transactional
    public void status(String actor, ResourceDefinition def, List<String> ids, String status, String ip) {
        notReadOnly(def);
        guard.require(actor, def.editPermission());
        if ("pool".equals(def.key())) throw new BusinessException("公海记录请通过领取/分配/回收操作变更状态");
        String next = text(status);
        if (next == null) throw new BusinessException("请选择目标状态");
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            Map<String, Object> row = mapper.find(id);
            if (row == null) continue;
            assertWritable(actor, def, row);
            mapper.updateStatus(id, next, actor);
            Map<String, Object> after = toRecord(row);
            after.put("status", next);
            audit(actor, def, id, "STATUS", toRecord(row), after, ip);
        }
    }

    private void mirror(ResourceDefinition def, Map<String, Object> payload, String dept) {
        if (!"customers".equals(def.key())) return;
        mapper.mirrorCustomer(String.valueOf(payload.get("id")), dept, text(payload.get("name")), text(payload.get("level")), text(payload.get("status")));
    }

    private Map<String, Object> mustFind(ResourceDefinition def, String id) {
        Map<String, Object> row = mapper.find(id);
        if (row == null || !def.key().equals(String.valueOf(row.get("resourceKey")))) throw new BusinessException(404, "记录不存在或已被删除");
        return row;
    }

    private void assertWritable(String actor, ResourceDefinition def, Map<String, Object> row) {
        if (def.ownerScoped()) return;
        if ("pool".equals(def.key())) return; // 公海记录由 crm:pool:edit 控制，领取走专用接口
        var scope = resolveScope(actor, def);
        if (scope.all()) return;
        if (scope.selfOnly()) {
            if (!ownedBy(actor, scope.userName(), row)) throw new BusinessException("无权操作他人负责的数据");
            return;
        }
        String dept = row.get("departmentId") == null ? null : String.valueOf(row.get("departmentId"));
        if (dept == null || !scope.deptIds().contains(dept)) throw new BusinessException("无权操作其他部门的数据");
    }

    /** 记录是否属于指定用户：优先 owner_id，其次 payload.owner 姓名。 */
    private boolean ownedBy(String actor, String userName, Map<String, Object> row) {
        if (row.get("ownerId") != null && actor.equals(String.valueOf(row.get("ownerId")))) return true;
        Object owner = read(String.valueOf(row.get("payload"))).get("owner");
        return owner != null && userName != null && userName.equals(String.valueOf(owner));
    }

    private DataScopeService.Scope resolveScope(String actor, ResourceDefinition def) {
        if ("pool".equals(def.key())) return DataScopeService.Scope.allScope();
        return dataScope.resolve(actor, moduleOf(def.key()));
    }

    /** 资源到数据范围模块的映射（报价/订单/客户等分别配置）。 */
    private static String moduleOf(String key) {
        return switch (key) {
            case "leads" -> "leads";
            case "opportunities" -> "opportunities";
            case "quotes", "returns" -> "quotes";
            case "orders", "shipments", "payments", "receivables" -> "orders";
            case "reports-sales", "reports-customer-purchase", "reports-product-sales" -> "reports";
            default -> "customers";
        };
    }

    private String resolveWriteDept(String actor, ResourceDefinition def, Object requested) {
        if (def.ownerScoped()) return ownDept(actor);
        if (resolveScope(actor, def).all()) {
            String requestedDept = text(requested);
            if (requestedDept == null) throw new BusinessException("请选择所属部门");
            if (depts.find(requestedDept) == null) throw new BusinessException("所属部门不存在");
            return requestedDept;
        }
        String own = ownDept(actor);
        if (own == null) throw new BusinessException("当前账号未关联部门，无法创建业务记录");
        return own;
    }

    private String ownDept(String actor) {
        SysUser u = users.findById(actor);
        return u == null || u.getDeptId() == null || u.getDeptId().isBlank() ? null : u.getDeptId();
    }

    private Map<String, String> filters(Map<String, String> params) {
        Map<String, String> out = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (RESERVED.contains(k) || v == null || v.isBlank()) return;
            if (!k.matches("[A-Za-z][A-Za-z0-9_]{0,39}")) return;
            out.put(k, v);
        });
        return out;
    }

    private static String orderField(String sortBy) {
        String v = text(sortBy);
        return v != null && v.matches("[A-Za-z][A-Za-z0-9_]{0,39}") ? v : null;
    }

    /** 敏感价格字段（最低成交价/成本/毛利）仅管理员与 crm:price:sensitive 角色可见。 */
    private void maskSensitive(String actor, Map<String, Object> row) {
        if (row == null) return;
        if (guard.has(actor, "system:data:all") || guard.has(actor, "crm:price:sensitive")) return;
        for (String field : List.of("minimumPrice", "cost", "purchaseCost", "margin", "purchasePrice")) {
            if (row.containsKey(field)) row.put(field, null);
        }
    }

    /** 公海记录是否需要对当前账号脱敏：拥有 crm:pool:contact 或全局开关打开时展示完整联系方式。 */
    private boolean maskContactFor(String actor, ResourceDefinition def, String view, Map<String, Object> row) {
        boolean pool = "pool".equals(def.key());
        boolean leadPool = "leads".equals(def.key()) && ("pool".equals(view) || isUnowned(row));
        if (!pool && !leadPool) return false;
        if (guard.has(actor, "crm:pool:contact")) return false;
        return !config.bool(ConfigService.POOL_CONTACT_FULL, false);
    }

    private boolean isUnowned(Map<String, Object> row) {
        Object ownerId = row.get("ownerId");
        if (ownerId != null && !String.valueOf(ownerId).isBlank()) return false;
        Object owner = row.get("owner");
        if (owner == null && row.containsKey("payload")) owner = read(String.valueOf(row.get("payload"))).get("owner");
        return owner == null || String.valueOf(owner).isBlank();
    }

    private void maskContact(Map<String, Object> row) {
        for (String field : List.of("phone", "wechat")) {
            Object value = row.get(field);
            if (value != null && !String.valueOf(value).isBlank()) row.put(field, maskValue(String.valueOf(value)));
        }
    }

    /** 手机号/座机保留区号与前三后四，中间用 * 代替。 */
    static String maskValue(String value) {
        String v = value.trim();
        int dash = v.indexOf('-');
        String prefix = dash > 0 ? v.substring(0, dash + 1) : "";
        String rest = dash > 0 ? v.substring(dash + 1) : v;
        if (rest.length() <= 4) return prefix + "****";
        return prefix + rest.substring(0, Math.min(3, rest.length() - 4)) + "****" + rest.substring(rest.length() - 4);
    }

    private Map<String, Object> toRecord(Map<String, Object> row) {
        Map<String, Object> payload = read(String.valueOf(row.get("payload")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        payload.forEach((k, v) -> { if (!"id".equals(k)) out.put(k, v); });
        if (out.get("departmentId") == null && row.get("departmentId") != null) out.put("departmentId", row.get("departmentId"));
        if (out.get("status") == null && row.get("metaStatus") != null) out.put("status", row.get("metaStatus"));
        if (out.get("createdAt") == null) out.put("createdAt", date(row.get("createdAt")));
        out.put("updatedAt", date(row.get("updatedAt")));
        return out;
    }

    private String write(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException("数据格式不正确");
        }
    }

    private Map<String, Object> read(String payload) {
        try {
            return json.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String ownerIdOf(Object owner) {
        String name = text(owner);
        if (name == null) return null;
        return users.findByAccountOrPhone(name) == null ? null : users.findByAccountOrPhone(name).getUserId();
    }

    private String realName(String actor) {
        SysUser u = users.findById(actor);
        return u == null ? null : u.getRealName();
    }

    private void audit(String actor, ResourceDefinition def, String id, String action, Object before, Object after, String ip) {
        SysUser u = users.findById(actor);
        mapper.audit(actor, u == null ? null : u.getUsername(), u == null ? null : u.getDeptId(), def.key(), id, action,
                before == null ? null : writeAny(before), after == null ? null : writeAny(after), "SUCCESS", ip);
    }

    private String writeAny(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private void checkLeadDuplicate(String phone, String name) {
        if (phone != null && !phone.isBlank()) {
            String clean = phone.replaceAll("[^0-9]", "");
            if (clean.length() >= 7) {
                var byPhone = mapper.findByPhone(clean);
                if (byPhone != null && !byPhone.isEmpty()) {
                    Map<String, Object> dup = toRecord(byPhone.get(0));
                    String owner = text(dup.get("owner"));
                    String dupName = text(dup.get("name") != null ? dup.get("name") : dup.get("company"));
                    throw new BusinessException(400, "检测到手机号已存在关联记录【" + (dupName != null ? dupName : "已有记录") + "】（负责人：" + (owner != null ? owner : "无") + "），如确认强制录入请勾选忽略重复");
                }
            }
        }
        if (name != null && !name.isBlank()) {
            var byName = mapper.findByName(name.trim());
            if (byName != null && !byName.isEmpty()) {
                Map<String, Object> dup = toRecord(byName.get(0));
                String owner = text(dup.get("owner"));
                throw new BusinessException(400, "检测到客户/门店名称【" + name.trim() + "】已存在（负责人：" + (owner != null ? owner : "无") + "），如确认强制录入请勾选忽略重复");
            }
        }
    }

    private static void notReadOnly(ResourceDefinition def) {
        if (def.readOnly()) throw new BusinessException("报表为只读资源，不能修改");
    }

    private static String date(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }

    public record PageResult<T>(List<T> rows, long total) {}
}
