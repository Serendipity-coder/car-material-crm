package com.checa.crm.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConfigMapper {
    @Select("select config_key configKey,config_value configValue,remark from sys_config order by config_key")
    List<Map<String, Object>> all();

    @Select("select config_value from sys_config where config_key=#{key}")
    String value(@Param("key") String key);

    @Insert("insert into sys_config(config_key,config_value,updated_by) values(#{key},#{value},#{actor}) " +
            "on duplicate key update config_value=values(config_value),updated_by=values(updated_by)")
    int upsert(@Param("key") String key, @Param("value") String value, @Param("actor") String actor);
}
