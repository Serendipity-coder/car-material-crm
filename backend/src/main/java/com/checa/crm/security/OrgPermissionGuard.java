package com.checa.crm.security;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

@Component
public class OrgPermissionGuard {
    private final SysUserMapper users;
    public OrgPermissionGuard(SysUserMapper users) { this.users = users; }
    public void require(String userId, String permission) {
        if (userId == null || userId.isBlank()) throw new BusinessException(401, "请先登录");
        var codes = users.permissions(userId);
        if (!codes.contains("*") && !codes.contains(permission)) throw new BusinessException(403, "无权限执行该操作");
    }

    /** 是否拥有某个权限（用于字段级裁剪等不需要抛错的场景）。 */
    public boolean has(String userId, String permission) {
        if (userId == null || userId.isBlank()) return false;
        var codes = users.permissions(userId);
        return codes.contains("*") || codes.contains(permission);
    }
}
