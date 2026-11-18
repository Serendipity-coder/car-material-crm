package com.checa.crm.resource;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface GenericRecordMapper {

    String WHERE = " from crm_record where deleted=0 and resource_key=#{key}"
            + "<if test='deptIds!=null'> and department_id in <foreach collection='deptIds' item='d' open='(' separator=',' close=')'>#{d}</foreach></if>"
            + "<if test='ownerName!=null'> and (json_unquote(json_extract(payload,'$.owner'))=#{ownerName} or created_by=#{actor})</if>"
            + "<if test='selfId!=null'> and (owner_id=#{selfId} or json_unquote(json_extract(payload,'$.owner'))=#{selfName})</if>"
            + "<if test='poolOnly'> and (owner_id is null or owner_id='') and (json_unquote(json_extract(payload,'$.owner')) is null or json_unquote(json_extract(payload,'$.owner'))='')</if>"
            + "<if test='keyword!=null and keyword!=\"\"'> and cast(payload as char) like concat('%',#{keyword},'%')</if>"
            + "<foreach collection='filters' index='k' item='v'> and json_unquote(json_extract(payload, concat('$.', #{k})))=#{v}</foreach>";

    @Select({"<script>",
            "select record_id id, cast(payload as char) payload, department_id departmentId, status metaStatus, owner_id ownerId, created_by createdBy, created_at createdAt, updated_at updatedAt",
            WHERE,
            "<if test='orderBy!=null'> order by json_unquote(json_extract(payload, concat('$.', #{orderBy}))) ${direction}</if>",
            "<if test='orderBy==null'> order by created_at desc, record_id</if>",
            "limit #{offset},#{size}",
            "</script>"})
    List<Map<String, Object>> page(@Param("key") String key, @Param("deptIds") List<String> deptIds, @Param("actor") String actor,
                                   @Param("ownerName") String ownerName, @Param("selfId") String selfId, @Param("selfName") String selfName,
                                   @Param("poolOnly") boolean poolOnly, @Param("keyword") String keyword,
                                   @Param("filters") Map<String, String> filters,
                                   @Param("orderBy") String orderBy, @Param("direction") String direction,
                                   @Param("offset") int offset, @Param("size") int size);

    @Select({"<script>", "select count(*)", WHERE, "</script>"})
    long count(@Param("key") String key, @Param("deptIds") List<String> deptIds, @Param("actor") String actor,
               @Param("ownerName") String ownerName, @Param("selfId") String selfId, @Param("selfName") String selfName,
               @Param("poolOnly") boolean poolOnly, @Param("keyword") String keyword,
               @Param("filters") Map<String, String> filters);

    @Select("select record_id id, resource_key resourceKey, cast(payload as char) payload, department_id departmentId, status metaStatus, owner_id ownerId, created_by createdBy from crm_record where record_id=#{id} and deleted=0")
    Map<String, Object> find(@Param("id") String id);

    @Insert("insert into crm_record(record_id,resource_key,payload,department_id,owner_id,status,created_by,updated_by,deleted) values(#{id},#{key},#{payload},#{dept},#{ownerId},#{status},#{actor},#{actor},0)")
    int insert(@Param("id") String id, @Param("key") String key, @Param("payload") String payload,
               @Param("dept") String dept, @Param("ownerId") String ownerId, @Param("status") String status, @Param("actor") String actor);

    @Update("update crm_record set payload=#{payload},department_id=#{dept},owner_id=#{ownerId},status=#{status},updated_by=#{actor} where record_id=#{id} and deleted=0")
    int update(@Param("id") String id, @Param("payload") String payload, @Param("dept") String dept,
               @Param("ownerId") String ownerId, @Param("status") String status, @Param("actor") String actor);

    @Update("update crm_record set payload=json_set(payload,'$.status',#{status}),status=#{status},updated_by=#{actor} where record_id=#{id} and deleted=0")
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("actor") String actor);

    @Update("update crm_record set deleted=1,updated_by=#{actor} where record_id=#{id} and deleted=0")
    int softDelete(@Param("id") String id, @Param("actor") String actor);

    @Insert("insert into crm_customer(customer_id,department_id,customer_name,customer_level,status,deleted) values(#{id},#{dept},#{name},#{level},#{status},0) on duplicate key update department_id=values(department_id),customer_name=values(customer_name),customer_level=values(customer_level),status=values(status),deleted=0")
    int mirrorCustomer(@Param("id") String id, @Param("dept") String dept, @Param("name") String name, @Param("level") String level, @Param("status") String status);

    @Update("update crm_customer set deleted=1 where customer_id=#{id}")
    int removeCustomerMirror(@Param("id") String id);

    @Insert("insert into audit_log(operator_id,operator_account,operator_dept_id,resource_key,record_id,action_type,before_json,after_json,result,client_ip) values(#{operatorId},#{account},#{deptId},#{key},#{recordId},#{action},#{before},#{after},#{result},#{ip})")
    int audit(@Param("operatorId") String operatorId, @Param("account") String account, @Param("deptId") String deptId,
              @Param("key") String key, @Param("recordId") String recordId, @Param("action") String action,
              @Param("before") String before, @Param("after") String after, @Param("result") String result, @Param("ip") String ip);

    @Select("select record_id id, resource_key resourceKey, cast(payload as char) payload, department_id departmentId, status metaStatus, owner_id ownerId, created_by createdBy, created_at createdAt, updated_at updatedAt from crm_record where deleted=0 and resource_key=#{key} order by created_at desc")
    List<Map<String, Object>> listByKey(@Param("key") String key);

    @Select("select record_id id, resource_key resourceKey, cast(payload as char) payload, department_id departmentId, status metaStatus, owner_id ownerId, created_by createdBy from crm_record where deleted=0 and resource_key=#{key} and (json_unquote(json_extract(payload, '$.customerId')) = #{customerId} or json_unquote(json_extract(payload, '$.customer')) = #{customerId}) order by created_at desc")
    List<Map<String, Object>> listByCustomerId(@Param("key") String key, @Param("customerId") String customerId);

    @Select("select record_id id, resource_key resourceKey, cast(payload as char) payload, department_id departmentId, status metaStatus, owner_id ownerId, created_by createdBy from crm_record where deleted=0 and resource_key in ('leads','customers') and (replace(replace(replace(json_unquote(json_extract(payload, '$.phone')), '-', ''), ' ', ''), '+86', '') = #{cleanPhone}) limit 5")
    List<Map<String, Object>> findByPhone(@Param("cleanPhone") String cleanPhone);

    @Select("select record_id id, resource_key resourceKey, cast(payload as char) payload, department_id departmentId, status metaStatus, owner_id ownerId, created_by createdBy from crm_record where deleted=0 and resource_key in ('leads','customers') and (json_unquote(json_extract(payload, '$.name')) = #{name} or json_unquote(json_extract(payload, '$.company')) = #{name} or json_unquote(json_extract(payload, '$.storeName')) = #{name}) limit 5")
    List<Map<String, Object>> findByName(@Param("name") String name);

    @Select({"<script>",
            "select log_id logId, operator_id operatorId, operator_account operatorAccount, operator_dept_id operatorDeptId, resource_key resourceKey, record_id recordId, action_type actionType, result, client_ip clientIp, created_at createdAt from audit_log where 1=1",
            "<if test='account!=null and account!=\"\"'> and operator_account=#{account}</if>",
            "<if test='resource!=null and resource!=\"\"'> and resource_key=#{resource}</if>",
            "<if test='action!=null and action!=\"\"'> and action_type=#{action}</if>",
            "<if test='start!=null and start!=\"\"'> and created_at>=#{start}</if>",
            "<if test='end!=null and end!=\"\"'> and created_at&lt;=concat(#{end},' 23:59:59')</if>",
            "order by log_id desc limit #{offset},#{size}",
            "</script>"})
    List<Map<String, Object>> auditPage(@Param("account") String account, @Param("resource") String resource, @Param("action") String action, @Param("start") String start, @Param("end") String end, @Param("offset") int offset, @Param("size") int size);

    @Select({"<script>", "select count(*) from audit_log where 1=1",
            "<if test='account!=null and account!=\"\"'> and operator_account=#{account}</if>",
            "<if test='resource!=null and resource!=\"\"'> and resource_key=#{resource}</if>",
            "<if test='action!=null and action!=\"\"'> and action_type=#{action}</if>",
            "<if test='start!=null and start!=\"\"'> and created_at>=#{start}</if>",
            "<if test='end!=null and end!=\"\"'> and created_at&lt;=concat(#{end},' 23:59:59')</if>",
            "</script>"})
    long auditCount(@Param("account") String account, @Param("resource") String resource, @Param("action") String action, @Param("start") String start, @Param("end") String end);

    /** 按 record_id 批量取 payload（供操作日志「记录」列解析可读名称）。 */
    @Select({"<script>",
            "select record_id id, resource_key resourceKey, cast(payload as char) payload from crm_record where record_id in",
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>",
            "</script>"})
    List<Map<String, Object>> payloadsByIds(@Param("ids") List<String> ids);

    /** 客户等级（供客户采购分析补充「高价值客户」判定）。 */
    @Select("select customer_name name, customer_level level from crm_customer where deleted=0")
    List<Map<String, Object>> customerLevels();

    @Select("select count(*) from crm_record where deleted=0 and resource_key='orders' and (json_unquote(json_extract(payload, '$.quoteNo')) = #{quoteNo} or json_unquote(json_extract(payload, '$.quoteId')) = #{quoteId} or json_unquote(json_extract(payload, '$.quoteNo')) = #{quoteId})")
    int countOrdersByQuote(@Param("quoteNo") String quoteNo, @Param("quoteId") String quoteId);
}
