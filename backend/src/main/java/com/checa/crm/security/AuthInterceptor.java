package com.checa.crm.security;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class AuthInterceptor implements HandlerInterceptor {
    private final JwtTokenService jwt; private final StringRedisTemplate redis; private final SysUserMapper users;
    public AuthInterceptor(JwtTokenService jwt,StringRedisTemplate redis,SysUserMapper users){this.jwt=jwt;this.redis=redis;this.users=users;}
    @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){if("OPTIONS".equalsIgnoreCase(req.getMethod()))return true;String path=req.getRequestURI().substring(req.getContextPath().length());if(path.startsWith("/auth/login")||path.equals("/auth/register")||path.equals("/auth/register/send-code")||path.equals("/api/sms/send-code")||path.equals("/auth/sms/send-code"))return true;String h=req.getHeader("Authorization");if(h==null||!h.startsWith("Bearer "))throw new BusinessException(401,"请先登录");String token=h.substring(7);if(Boolean.TRUE.equals(redis.hasKey("auth:revoked:"+sha(token))))throw new BusinessException(401,"登录会话已失效");String userId=jwt.subject(token);SysUser u=users.findById(userId);if(u==null)throw new BusinessException(401,"用户不存在或已注销");if(!"0".equals(u.getStatus()))throw new BusinessException(403,"该账号已停用");req.setAttribute("userId",userId);req.setAttribute("accessToken",token);return true;}
    public static String sha(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
