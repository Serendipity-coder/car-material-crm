package com.checa.crm.mapper;

import com.checa.crm.pojo.entity.OrgEntities;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrgRoleMapper {
 @Select("select role_id roleId,role_name roleName,role_key roleKey,sort,status,remark from sys_role where deleted=0 order by sort,role_id") List<OrgEntities.Role> list();
 @Select("select role_id roleId,role_name roleName,role_key roleKey,sort,status,remark from sys_role where role_id=#{id} and deleted=0") OrgEntities.Role find(@Param("id") String id);
 @Select("select count(*) from sys_role where role_key=#{key} and role_id<>#{id}") int countKey(@Param("id") String id,@Param("key") String key);
 @Select("select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id and u.deleted=0 where ur.role_id=#{id}") int memberCount(@Param("id") String id);
 @Select("select menu_id from sys_role_menu where role_id=#{id}") List<String> menuIds(@Param("id") String id);
 @Select("select menu_id menuId,parent_id parentId,menu_name menuName,menu_type menuType,perms,sort,status from sys_menu where deleted=0 and status='0' order by sort,menu_id") List<OrgEntities.Menu> menus();
 @Select("select count(*) from sys_menu where deleted=0 and menu_id in (select menu_id from sys_role_menu where role_id=#{roleId})") int validMenuCount(@Param("roleId") String roleId);
 @Insert("insert into sys_role(role_id,role_name,role_key,sort,status,remark,deleted) values(#{id},#{name},#{key},#{sort},#{status},#{remark},0)") int insert(@Param("id") String id,@Param("name") String name,@Param("key") String key,@Param("sort") Integer sort,@Param("status") String status,@Param("remark") String remark);
 @Update("update sys_role set role_name=#{name},role_key=#{key},sort=#{sort},status=#{status},remark=#{remark} where role_id=#{id} and deleted=0") int update(@Param("id") String id,@Param("name") String name,@Param("key") String key,@Param("sort") Integer sort,@Param("status") String status,@Param("remark") String remark);
 @Delete("delete from sys_role_menu where role_id=#{id}") int clearMenus(@Param("id") String id);
 @Insert("insert into sys_role_menu(role_id,menu_id) values(#{roleId},#{menuId})") int bindMenu(@Param("roleId") String roleId,@Param("menuId") String menuId);
 @Update("update sys_role set deleted=1 where role_id=#{id}") int delete(@Param("id") String id);
 // ---------------- 角色产品品类授权 ----------------
 @Select({"<script>","select role_id roleId,category_id categoryId,include_children includeChildren from sys_role_category where role_id in <foreach collection='roleIds' item='r' open='(' separator=',' close=')'>#{r}</foreach>","</script>"}) List<OrgEntities.RoleCategory> categoryBindings(@Param("roleIds") List<String> roleIds);
 @Select("select category_id categoryId,parent_id parentId,category_name categoryName,sort_num sortNum,status from crm_category where deleted=0 order by sort_num,category_id") List<OrgEntities.CategoryNode> categoryNodes();
 @Delete("delete from sys_role_category where role_id=#{roleId}") int clearCategories(@Param("roleId") String roleId);
 @Insert("insert into sys_role_category(role_id,category_id,include_children) values(#{roleId},#{categoryId},#{includeChildren})") int bindCategory(@Param("roleId") String roleId,@Param("categoryId") String categoryId,@Param("includeChildren") Integer includeChildren);
 // ---------------- 角色数据范围 ----------------
 @Select({"<script>","select role_id roleId,module_key moduleKey,scope_type scopeType,scope_value scopeValue from sys_role_data_scope where role_id in <foreach collection='roleIds' item='r' open='(' separator=',' close=')'>#{r}</foreach>","</script>"}) List<OrgEntities.RoleDataScope> dataScopes(@Param("roleIds") List<String> roleIds);
 @Delete("delete from sys_role_data_scope where role_id=#{roleId}") int clearDataScopes(@Param("roleId") String roleId);
 @Insert("insert into sys_role_data_scope(role_id,module_key,scope_type,scope_value) values(#{roleId},#{moduleKey},#{scopeType},#{scopeValue})") int bindDataScope(@Param("roleId") String roleId,@Param("moduleKey") String moduleKey,@Param("scopeType") String scopeType,@Param("scopeValue") String scopeValue);
 @Select("<script>select role_id id, role_name name from sys_role where role_id in <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>") List<Map<String,Object>> namesByIds(@Param("ids") List<String> ids);
}
