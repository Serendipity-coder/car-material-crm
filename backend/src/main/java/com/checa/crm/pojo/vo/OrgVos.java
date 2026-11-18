package com.checa.crm.pojo.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrgVos {
    private OrgVos() {}
    public record User(String id,String name,String account,String departmentId,String position,List<String> roleIds,String phone,String email,String status,LocalDate hireDate,String gender,String remark,String lastLoginIp,LocalDateTime lastLoginTime) {}
    public record Dept(String id,String parentId,String name,String code,String leaderId,Integer sort,String phone,String email,String status,List<Dept> children,Integer employeeCount) {}
    public record Role(String id,String name,String key,Integer sort,String status,Integer memberCount,List<String> menuIds,String remark,boolean builtin) {}
    public record Menu(String id,String parentId,String name,String type,String permission,Integer sort,List<Menu> children) {}
    public record Page<T>(List<T> rows,long total) {}
    public record UserSummary(long total,long active,long departments,long newThisMonth) {}
    /** 角色可访问的产品品类授权项。 */
    public record RoleCategoryBinding(String categoryId,boolean includeChildren) {}
    /** 角色品类授权：all=true 表示全部品类。 */
    public record RoleCategories(boolean all,List<RoleCategoryBinding> bindings) {}
    /** 品类树节点（角色授权界面）。 */
    public record CategoryNode(String id,String parentId,String name,Integer sort,String status,List<CategoryNode> children) {}
    /** 角色数据范围：模块 + 档位（SELF/DEPT/DEPT_AND_CHILD/ALL/CUSTOM）。 */
    public record RoleDataScopeItem(String moduleKey,String scopeType,String scopeValue) {}
    public record RoleDataScopes(List<RoleDataScopeItem> items) {}
}
