package com.checa.crm.pojo.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OrgEntities {
    private OrgEntities() {}
    public static class User {
        public String userId, deptId, username, realName, gender, phone, email, position, status, remark, lastLoginIp;
        public LocalDate hireDate; public LocalDateTime createTime, updateTime, lastLoginTime;
    }
    public static class Dept {
        public String deptId, parentId, ancestors, deptName, deptCode, leaderId, phone, email, status; public Integer orderNum;
    }
    public static class Role {
        public String roleId, roleName, roleKey, status, remark; public Integer sort;
    }
    public static class Menu {
        public String menuId, parentId, menuName, menuType, perms, status; public Integer sort;
    }
    /** 角色可访问的产品品类（category_id='0' 表示全部品类）。 */
    public static class RoleCategory {
        public String roleId, categoryId; public Integer includeChildren;
    }
    /** 角色数据范围：SELF 本人 / DEPT 本部门 / DEPT_AND_CHILD 本部门及下级 / ALL 全企业 / CUSTOM 指定部门。 */
    public static class RoleDataScope {
        public String roleId, moduleKey, scopeType, scopeValue;
    }
    /** 品类树节点（用于角色品类授权界面）。 */
    public static class CategoryNode {
        public String categoryId, parentId, categoryName, status; public Integer sortNum;
    }
}
