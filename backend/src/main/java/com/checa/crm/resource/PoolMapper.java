package com.checa.crm.resource;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 公海相关的原子更新与历史记录。
 * 领取使用单条 UPDATE + 条件（负责人为空且状态属于公海），由数据库保证同一时刻只有一个人能领到。
 */
@Mapper
public interface PoolMapper {

    @Update("update crm_record set payload=json_set(payload,'$.owner',#{owner},'$.status',#{status},'$.claimedAt',#{date})," +
            "owner_id=#{userId},status=#{status},updated_by=#{actor} " +
            "where record_id=#{id} and resource_key=#{resource} and deleted=0 " +
            "and (owner_id is null or owner_id='') " +
            "and (json_unquote(json_extract(payload,'$.owner')) is null or json_unquote(json_extract(payload,'$.owner'))='')")
    int claim(@Param("id") String id, @Param("resource") String resource, @Param("actor") String actor,
              @Param("userId") String userId, @Param("owner") String owner, @Param("status") String status, @Param("date") String date);

    @Update("update crm_record set payload=json_set(payload,'$.owner',#{owner},'$.status',#{status})," +
            "owner_id=#{userId},status=#{status},updated_by=#{actor} " +
            "where record_id=#{id} and resource_key=#{resource} and deleted=0")
    int assign(@Param("id") String id, @Param("resource") String resource, @Param("actor") String actor,
               @Param("userId") String userId, @Param("owner") String owner, @Param("status") String status);

    @Update("update crm_record set payload=json_remove(json_set(payload,'$.status','待分配'),'$.owner','$.claimedAt','$.ownerId')," +
            "owner_id=null,status='待分配',updated_by=#{actor} where record_id=#{id} and resource_key=#{resource} and deleted=0")
    int release(@Param("id") String id, @Param("resource") String resource, @Param("actor") String actor);

    @Insert("insert into crm_pool_log(record_id,resource_key,action,from_owner,to_owner,operator_id,operator_name,note) " +
            "values(#{recordId},#{resource},#{action},#{fromOwner},#{toOwner},#{operatorId},#{operatorName},#{note})")
    int log(@Param("recordId") String recordId, @Param("resource") String resource, @Param("action") String action,
            @Param("fromOwner") String fromOwner, @Param("toOwner") String toOwner,
            @Param("operatorId") String operatorId, @Param("operatorName") String operatorName, @Param("note") String note);

    @Select("select log_id logId,record_id recordId,resource_key resourceKey,action,from_owner fromOwner,to_owner toOwner," +
            "operator_id operatorId,operator_name operatorName,note,created_at createdAt " +
            "from crm_pool_log where record_id=#{id} order by log_id desc")
    List<Map<String, Object>> history(@Param("id") String id);

    @Select("select record_id from crm_record where resource_key='pool' and deleted=0 " +
            "and json_unquote(json_extract(payload,'$.name'))=#{name} " +
            "and (owner_id is null or owner_id='') limit 1")
    String openPoolByName(@Param("name") String name);
}
