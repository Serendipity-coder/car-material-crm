package com.checa.crm.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public final class OrgDtos {
    private OrgDtos() {}
    public record UserQuery(Integer pageNum, Integer pageSize, String keyword, String deptId, String roleId, String status) {}
    public record UserCreate(@JsonAlias("realName") String name, @JsonAlias("username") String account, String password, @JsonAlias("deptId") String departmentId, String position, List<String> roleIds, String phone, String email, String status, String hireDate, String gender, String remark) {}
    public record UserUpdate(@JsonAlias("realName") String name, @JsonAlias("username") String account, String password, @JsonAlias("deptId") String departmentId, String position, List<String> roleIds, String phone, String email, String status, String hireDate, String gender, String remark) {}
    public record StatusBatch(List<String> ids, String status) {}
    public record DeptSave(@JsonAlias("deptName") String name, String parentId, @JsonAlias({"deptCode","code"}) String code, @JsonAlias("orderNum") Integer sort, String leaderId, String phone, String email, String status) {}
    public record RoleSave(@JsonAlias("roleName") String name, @JsonAlias("roleKey") String key, Integer sort, String status, String remark) {}
    public record RoleMenus(List<String> menuIds) {}
    /** 角色品类授权：all=true 表示全部品类；否则按 categoryIds 授权，includeChildren 控制是否连同下级。 */
    public record RoleCategorySave(Boolean all,List<String> categoryIds,Boolean includeChildren) {}
    public record DataScopeItem(String moduleKey,String scopeType,String scopeValue) {}
    /** 角色数据范围：按模块覆盖式保存。 */
    public record RoleDataScopeSave(List<DataScopeItem> items) {}
}
