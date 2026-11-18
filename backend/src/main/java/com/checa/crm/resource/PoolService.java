package com.checa.crm.resource;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.SysUser;
import com.checa.crm.security.OrgPermissionGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 公海服务：线索公海与客户公海共用一套领取/分配/回收规则。
 * <ul>
 *   <li>公海读取为企业内共享（不受个人归属与部门限制）；</li>
 *   <li>领取使用单条原子 UPDATE，负责人已存在时返回 409，避免多人同时抢占；</li>
 *   <li>领取、分配、回收均写入 {@code crm_pool_log}，保留历史。</li>
 * </ul>
 */
@Service
public class PoolService {
    private final PoolMapper pool;
    private final GenericRecordMapper records;
    private final SysUserMapper users;
    private final OrgPermissionGuard guard;
    private final ObjectMapper json;

    public PoolService(PoolMapper pool, GenericRecordMapper records, SysUserMapper users, OrgPermissionGuard guard, ObjectMapper json) {
        this.pool = pool; this.records = records; this.users = users; this.guard = guard; this.json = json;
    }

    // ---------------- 客户公海 ----------------
    @Transactional
    public Map<String, Object> claimPool(String actor, String id) {
        guard.require(actor, "crm:pool:edit");
        SysUser user = mustUser(actor);
        int updated = pool.claim(id, "pool", actor, user.getUserId(), user.getRealName(), "已领取", LocalDate.now().toString());
        if (updated == 0) {
            Map<String, Object> row = records.find(id);
            if (row == null || !"pool".equals(String.valueOf(row.get("resourceKey"))))
                throw new BusinessException(404, "公海记录不存在");
            throw new BusinessException(409, "该记录已被他人领取，请刷新后重试");
        }
        pool.log(id, "pool", "CLAIM", null, user.getRealName(), actor, user.getRealName(), "公海领取");
        return view(id);
    }

    @Transactional
    public Map<String, Object> assignPool(String actor, String id, String owner) {
        guard.require(actor, "crm:pool:assign");
        SysUser operator = mustUser(actor);
        SysUser target = findUser(owner);
        if (target == null) throw new BusinessException("负责人不存在或已停用");
        Map<String, Object> row = records.find(id);
        if (row == null || !"pool".equals(String.valueOf(row.get("resourceKey")))) throw new BusinessException(404, "公海记录不存在");
        String from = ownerOf(row);
        pool.assign(id, "pool", actor, target.getUserId(), target.getRealName(), "已分配");
        pool.log(id, "pool", "ASSIGN", from, target.getRealName(), actor, operator.getRealName(), "公海分配");
        return view(id);
    }

    /** 把客户档案回收到客户公海（生成一条待分配的公有记录）。 */
    @Transactional
    public Map<String, Object> recycleCustomer(String actor, String customerId) {
        guard.require(actor, "crm:pool:assign");
        SysUser operator = mustUser(actor);
        String id = recycleCustomerInternal(customerId, operator.getUserId(), operator.getRealName(), "RECYCLE", "客户档案回收：");
        if (id == null) throw new BusinessException("该客户已在客户公海中，请勿重复回收");
        return view(id);
    }

    /** 回收客户到公海（内部实现，供手动回收与定时任务共用）；已在公海时返回 null。 */
    @Transactional
    public String recycleCustomerInternal(String customerId, String operatorId, String operatorName, String action, String notePrefix) {
        Map<String, Object> row = records.find(customerId);
        if (row == null || !"customers".equals(String.valueOf(row.get("resourceKey"))))
            throw new BusinessException(404, "客户档案不存在");
        Map<String, Object> payload = read(String.valueOf(row.get("payload")));
        String name = text(payload.get("name"));
        if (name == null) throw new BusinessException("客户名称为空，无法回收");
        if (pool.openPoolByName(name) != null) return null;
        String from = text(payload.get("owner"));
        String id = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        payload.put("id", id);
        payload.remove("owner");
        payload.remove("claimedAt");
        payload.remove("ownerId");
        payload.put("status", "待分配");
        records.insert(id, "pool", write(payload), row.get("departmentId") == null ? null : String.valueOf(row.get("departmentId")), null, "待分配", operatorId);
        pool.log(id, "pool", action, from, null, operatorId, operatorName, notePrefix + name);
        return id;
    }

    // ---------------- 线索公海 ----------------
    @Transactional
    public Map<String, Object> claimLead(String actor, String id) {
        guard.require(actor, "crm:lead:edit");
        SysUser user = mustUser(actor);
        int updated = pool.claim(id, "leads", actor, user.getUserId(), user.getRealName(), "进行中", LocalDate.now().toString());
        if (updated == 0) {
            Map<String, Object> row = records.find(id);
            if (row == null || !"leads".equals(String.valueOf(row.get("resourceKey")))) throw new BusinessException(404, "线索不存在");
            throw new BusinessException(409, "该线索已被他人领取，请刷新后重试");
        }
        pool.log(id, "leads", "CLAIM", null, user.getRealName(), actor, user.getRealName(), "线索公海领取");
        return view(id);
    }

    /** 回收线索到公海（清空负责人，保留线索记录）。 */
    @Transactional
    public Map<String, Object> releaseLead(String actor, String id, String note) {
        guard.require(actor, "crm:pool:assign");
        SysUser operator = mustUser(actor);
        Map<String, Object> row = records.find(id);
        if (row == null || !"leads".equals(String.valueOf(row.get("resourceKey")))) throw new BusinessException(404, "线索不存在");
        String from = ownerOf(row);
        pool.release(id, "leads", actor);
        pool.log(id, "leads", "RECYCLE", from, null, actor, operator.getRealName(), note == null || note.isBlank() ? "线索回收至公海" : note.trim());
        return view(id);
    }

    public List<Map<String, Object>> history(String actor, String id, String resource) {
        guard.require(actor, "leads".equals(resource) ? "crm:lead:list" : "crm:pool:list");
        return pool.history(id);
    }

    // ---------------- 工具 ----------------
    private SysUser mustUser(String actor) {
        SysUser user = users.findById(actor);
        if (user == null) throw new BusinessException(401, "请先登录");
        return user;
    }

    private SysUser findUser(String value) {
        if (value == null || value.isBlank()) return null;
        SysUser byName = users.findByName(value.trim());
        return byName != null ? byName : users.findByAccountOrPhone(value.trim());
    }

    private String ownerOf(Map<String, Object> row) {
        return text(read(String.valueOf(row.get("payload"))).get("owner"));
    }

    private Map<String, Object> view(String id) {
        Map<String, Object> row = records.find(id);
        if (row == null) throw new BusinessException(404, "记录不存在");
        Map<String, Object> payload = read(String.valueOf(row.get("payload")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        payload.forEach((k, v) -> { if (!"id".equals(k)) out.put(k, v); });
        if (out.get("departmentId") == null && row.get("departmentId") != null) out.put("departmentId", row.get("departmentId"));
        if (out.get("status") == null && row.get("metaStatus") != null) out.put("status", row.get("metaStatus"));
        return out;
    }

    private Map<String, Object> read(String payload) {
        try {
            return json.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String write(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException("数据格式不正确");
        }
    }

    private static String text(Object value) {
        if (value == null) return null;
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }
}
