package com.checa.crm.mapper;

import com.checa.crm.pojo.entity.SysUser;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper {
 @Select("select user_id as userId,dept_id as deptId,username,password,real_name as realName,gender,phone,email,position,status,hire_date as hireDate,deleted,create_time as createTime,update_time as updateTime from sys_user where (username=#{key} or phone=#{key}) and deleted=0 limit 1") SysUser findByAccountOrPhone(@Param("key") String key);
 @Select("select user_id as userId,dept_id as deptId,username,password,real_name as realName,gender,phone,email,position,status,hire_date as hireDate,deleted,create_time as createTime,update_time as updateTime from sys_user where user_id=#{id} and deleted=0 limit 1") SysUser findById(@Param("id") String id);
 @Select("select user_id as userId,dept_id as deptId,username,password,real_name as realName,gender,phone,email,position,status,hire_date as hireDate,deleted,create_time as createTime,update_time as updateTime from sys_user where real_name=#{name} and deleted=0 and status='0' limit 1") SysUser findByName(@Param("name") String name);
 @Select("select count(*) from sys_user where username=#{username}") int countByUsername(@Param("username") String username);
 @Select("select count(*) from sys_user where phone=#{phone} and deleted=0") int countByPhone(@Param("phone") String phone);
 @Select("select r.role_id from sys_user_role ur join sys_role r on r.role_id=ur.role_id where ur.user_id=#{userId} and r.status='0' and r.deleted=0") List<String> roleIds(@Param("userId") String userId);
 @Select("select r.role_name from sys_user_role ur join sys_role r on r.role_id=ur.role_id where ur.user_id=#{userId} and r.status='0' and r.deleted=0 order by r.sort") List<String> roleNames(@Param("userId") String userId);
 @Select("select distinct m.perms from sys_user_role ur join sys_role r on r.role_id=ur.role_id join sys_role_menu rm on rm.role_id=r.role_id join sys_menu m on m.menu_id=rm.menu_id where ur.user_id=#{userId} and r.status='0' and r.deleted=0 and m.status='0' and m.deleted=0 and m.perms is not null and m.perms<>''") List<String> permissions(@Param("userId") String userId);
 @Select("select dept_name from sys_dept where dept_id=#{deptId} and deleted=0 limit 1") String departmentName(@Param("deptId") String deptId);
 @Insert("insert into sys_user(user_id,dept_id,username,password,real_name,gender,phone,email,position,status,hire_date,deleted) values(#{userId},#{deptId},#{username},#{password},#{realName},'0',#{phone},#{email},#{position},'0',current_date,0)") int insert(SysUser user);
 @Insert("insert into sys_user_role(user_id,role_id) values(#{userId},#{roleId})") int bindRole(@Param("userId") String userId,@Param("roleId") String roleId);
 @Update("update sys_user set password=#{password} where user_id=#{userId}") int updatePassword(@Param("userId") String userId,@Param("password") String password);
 @Update("update sys_user set last_login_time=current_timestamp,last_login_ip=#{ip} where user_id=#{userId}") int updateLogin(@Param("userId") String userId,@Param("ip") String ip);
 @Update("update sys_user set real_name=coalesce(nullif(#{name},''),real_name),phone=coalesce(nullif(#{phone},''),phone),email=coalesce(nullif(#{email},''),email) where user_id=#{userId} and deleted=0") int updateProfile(@Param("userId") String userId,@Param("name") String name,@Param("phone") String phone,@Param("email") String email);
 @Select("select count(*) from sys_user where deleted=0 and phone=#{phone} and user_id<>#{userId}") int countPhoneExcept(@Param("phone") String phone,@Param("userId") String userId);
 @Select("<script>select user_id id, coalesce(nullif(real_name,''), username) name from sys_user where user_id in <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>") List<Map<String,Object>> namesByIds(@Param("ids") List<String> ids);
}
