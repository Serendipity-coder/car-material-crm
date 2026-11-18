package com.checa.crm.security;

import com.checa.crm.mapper.OrgDeptMapper;
import com.checa.crm.mapper.OrgRoleMapper;
import com.checa.crm.mapper.OrgUserMapper;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.OrgEntities;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 数据范围：按「角色 ↔ 模块」计算当前用户能看到哪些业务记录。
 * <p>范围档位（方案 5.9 / 5.10.3）：</p>
 * <ul>
 *   <li>{@code SELF} 本人负责；</li>
 *   <li>{@code DEPT} 本部门；</li>
 *   <li>{@code DEPT_AND_CHILD} 本部门及下级（历史默认值）；</li>
 *   <li>{@code ALL} 全企业；</li>
 *   <li>{@code CUSTOM} 指定部门（scope_value 逗号分隔）。</li>
 * </ul>
 * <p>多角色取并集：任一角色给到更大范围即生效。未配置时沿用「本部门及下级」，保证历史账号行为不变。</p>
 */
@Service
public class DataScopeService {

    /** 解析结果：all=true 表示全企业；selfOnly=true 表示只看本人；deptIds 为允许的部门集合。 */
    public record Scope(boolean all, List<String> deptIds, boolean selfOnly, String userName) {
        public static Scope allScope() { return new Scope(true, null, false, null); }
        public static Scope selfOnly(String userName) { return new Scope(false, List.of(), true, userName); }
    }

    private final SysUserMapper users;
    private final OrgUserMapper orgUsers;
    private final OrgDeptMapper depts;
    private final OrgRoleMapper roles;

    public DataScopeService(SysUserMapper users, OrgUserMapper orgUsers, OrgDeptMapper depts, OrgRoleMapper roles) {
        this.users = users; this.orgUsers = orgUsers; this.depts = depts; this.roles = roles;
    }

    public Scope resolve(String actor, String moduleKey) {
        if (actor == null || actor.isBlank()) return Scope.selfOnly(null);
        var permissions = users.permissions(actor);
        if (permissions.contains("*") || permissions.contains("system:data:all")) return Scope.allScope();
        var user = orgUsers.find(actor);
        String userName = user == null ? null : user.realName;
        String deptId = user == null ? null : user.deptId;
        var roleIds = users.roleIds(actor);
        List<OrgEntities.RoleDataScope> rows = roleIds.isEmpty() ? List.of()
                : roles.dataScopes(roleIds).stream().filter(r -> moduleKey.equals(r.moduleKey) || "*".equals(r.moduleKey)).toList();
        if (rows.isEmpty()) {
            // 未配置数据范围：沿用历史默认「本部门及下级」，避免老账号突然失去数据
            return new Scope(false, subtree(deptId), false, userName);
        }
        boolean all = false, self = false;
        Set<String> allowed = new LinkedHashSet<>();
        for (var row : rows) {
            String type = row.scopeType == null ? "" : row.scopeType;
            switch (type) {
                case "ALL" -> all = true;
                case "SELF" -> self = true;
                case "DEPT" -> { if (deptId != null && !deptId.isBlank()) allowed.add(deptId); }
                case "DEPT_AND_CHILD" -> allowed.addAll(subtree(deptId));
                case "CUSTOM" -> {
                    if (row.scopeValue != null) for (String v : row.scopeValue.split(",")) if (!v.isBlank()) allowed.add(v.trim());
                }
                default -> { /* 未知档位按未配置处理 */ }
            }
        }
        if (all) return Scope.allScope();
        if (allowed.isEmpty()) return new Scope(false, List.of(), true, userName);
        return new Scope(false, new ArrayList<>(allowed), false, userName);
    }

    /** 部门子树（含自身）。 */
    public List<String> subtree(String deptId) {
        if (deptId == null || deptId.isBlank()) return List.of();
        Set<String> out = new LinkedHashSet<>();
        collect(deptId, out);
        return new ArrayList<>(out);
    }

    private void collect(String id, Set<String> out) {
        if (!out.add(id)) return;
        for (var d : depts.list()) if (id.equals(d.parentId)) collect(d.deptId, out);
    }
}
