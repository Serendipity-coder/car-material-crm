package com.checa.crm.mapper;

import com.checa.crm.pojo.entity.OrgEntities;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrgDeptMapper {
 @Select("select dept_id deptId,parent_id parentId,ancestors,dept_name deptName,dept_code deptCode,order_num orderNum,leader_id leaderId,phone,email,status from sys_dept where deleted=0 order by order_num,dept_id") List<OrgEntities.Dept> list();
 @Select("select dept_id deptId,parent_id parentId,ancestors,dept_name deptName,dept_code deptCode,order_num orderNum,leader_id leaderId,phone,email,status from sys_dept where dept_id=#{id} and deleted=0") OrgEntities.Dept find(@Param("id") String id);
 @Select("select count(*) from sys_dept where deleted=0 and dept_id<>#{id} and dept_name=#{name}") int countName(@Param("id") String id,@Param("name") String name);
 @Select("select count(*) from sys_dept where deleted=0 and dept_id<>#{id} and dept_name=#{name} and parent_id=#{parentId}") int countSame(@Param("id") String id,@Param("name") String name,@Param("parentId") String parentId);
 @Select("select count(*) from sys_dept where deleted=0 and parent_id=#{id}") int countChildren(@Param("id") String id);
 @Select("select count(*) from sys_user where deleted=0 and dept_id=#{id}") int countUsers(@Param("id") String id);
 @Insert("insert into sys_dept(dept_id,parent_id,ancestors,dept_name,dept_code,order_num,leader_id,phone,email,status,deleted) values(#{id},#{parentId},#{ancestors},#{name},#{code},#{sort},#{leaderId},#{phone},#{email},#{status},0)") int insert(@Param("id") String id,@Param("parentId") String parentId,@Param("ancestors") String ancestors,@Param("name") String name,@Param("code") String code,@Param("sort") Integer sort,@Param("leaderId") String leaderId,@Param("phone") String phone,@Param("email") String email,@Param("status") String status);
 @Update("update sys_dept set parent_id=#{parentId},ancestors=#{ancestors},dept_name=#{name},dept_code=#{code},order_num=#{sort},leader_id=#{leaderId},phone=#{phone},email=#{email},status=#{status} where dept_id=#{id} and deleted=0") int update(@Param("id") String id,@Param("parentId") String parentId,@Param("ancestors") String ancestors,@Param("name") String name,@Param("code") String code,@Param("sort") Integer sort,@Param("leaderId") String leaderId,@Param("phone") String phone,@Param("email") String email,@Param("status") String status);
 @Update("update sys_dept set deleted=1 where dept_id=#{id}") int delete(@Param("id") String id);
 @Update("update sys_dept set ancestors=#{ancestors} where dept_id=#{id} and deleted=0") int updateAncestors(@Param("id") String id,@Param("ancestors") String ancestors);
 @Select("<script>select dept_id id, dept_name name from sys_dept where dept_id in <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>") List<Map<String,Object>> namesByIds(@Param("ids") List<String> ids);
}
