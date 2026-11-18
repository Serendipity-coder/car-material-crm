package com.checa.crm.mapper;

import com.checa.crm.pojo.entity.OrgEntities;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OrgUserMapper {
 @Select({"<script>","select user_id userId,dept_id deptId,username,real_name realName,gender,phone,email,position,status,hire_date hireDate,remark,last_login_time lastLoginTime,last_login_ip lastLoginIp,create_time createTime,update_time updateTime from sys_user where deleted=0", "<if test='keyword != null and keyword != \"\"'> and (username like concat('%',#{keyword},'%') or real_name like concat('%',#{keyword},'%') or phone like concat('%',#{keyword},'%') or email like concat('%',#{keyword},'%'))</if>", "<if test='deptId != null and deptId != \"\"'> and dept_id=#{deptId}</if>", "<if test='status != null and status != \"\"'> and status=#{status}</if>", "order by create_time desc", "</script>"}) List<OrgEntities.User> list(@Param("keyword") String keyword,@Param("deptId") String deptId,@Param("status") String status);
 @Select("select user_id userId,dept_id deptId,username,real_name realName,gender,phone,email,position,status,hire_date hireDate,remark,last_login_time lastLoginTime,last_login_ip lastLoginIp,create_time createTime,update_time updateTime from sys_user where user_id=#{id} and deleted=0") OrgEntities.User find(@Param("id") String id);
 @Select("select count(*) from sys_user where username=#{account}") int countAccount(@Param("account") String account);
 @Select("select count(*) from sys_user where deleted=0 and phone=#{phone} and user_id<>#{id}") int countPhone(@Param("phone") String phone,@Param("id") String id);
 @Select("select count(*) from sys_user where deleted=0 and dept_id=#{deptId}") int countDept(@Param("deptId") String deptId);
 @Select("select role_id from sys_user_role where user_id=#{userId}") List<String> roleIds(@Param("userId") String userId);
 @Insert("insert into sys_user(user_id,dept_id,username,password,real_name,gender,phone,email,position,status,hire_date,remark,deleted) values(#{userId},#{deptId},#{username},#{password},#{realName},#{gender},#{phone},#{email},#{position},#{status},str_to_date(nullif(#{hireDate},''),'%Y-%m-%d'),#{remark},0)") int insert(@Param("userId") String id,@Param("deptId") String deptId,@Param("username") String account,@Param("password") String password,@Param("realName") String name,@Param("gender") String gender,@Param("phone") String phone,@Param("email") String email,@Param("position") String position,@Param("status") String status,@Param("hireDate") String hireDate,@Param("remark") String remark);
 @Update({"<script>","update sys_user <set><if test='deptId != null'>dept_id=#{deptId},</if><if test='name != null'>real_name=#{name},</if><if test='gender != null'>gender=#{gender},</if><if test='phone != null'>phone=#{phone},</if><if test='email != null'>email=#{email},</if><if test='position != null'>position=#{position},</if><if test='status != null'>status=#{status},</if><if test='hireDate != null and hireDate != \"\"'>hire_date=str_to_date(nullif(#{hireDate},''),'%Y-%m-%d'),</if><if test='remark != null'>remark=#{remark},</if><if test='password != null and password != \"\"'>password=#{password},</if></set> where user_id=#{id} and deleted=0","</script>"}) int update(@Param("id") String id,@Param("deptId") String deptId,@Param("name") String name,@Param("gender") String gender,@Param("phone") String phone,@Param("email") String email,@Param("position") String position,@Param("status") String status,@Param("hireDate") String hireDate,@Param("remark") String remark,@Param("password") String password);
 @Delete("delete from sys_user_role where user_id=#{userId}") int clearRoles(@Param("userId") String userId);
 @Insert("insert into sys_user_role(user_id,role_id) values(#{userId},#{roleId})") int bindRole(@Param("userId") String userId,@Param("roleId") String roleId);
 @Update("update sys_user set status=#{status} where user_id=#{id} and deleted=0") int updateStatus(@Param("id") String id,@Param("status") String status);
 @Update("update sys_user set deleted=1 where user_id=#{id}") int softDelete(@Param("id") String id);
}
