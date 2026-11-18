package com.checa.crm.pojo.vo;

import java.util.List;

public final class AuthVos {
    private AuthVos() {}
    public record UserVO(String id,String name,String account,String role,List<String> roles,List<String> roleIds,String department,String departmentId,String position,String phone,String email,List<String> permissionCodes) {}
    public record SessionVO(String accessToken,UserVO user) {}
    public record CodeVO(String message,String demoCode,long expireSeconds) {}
}
