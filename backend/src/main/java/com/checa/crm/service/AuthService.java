package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.dto.AuthDtos.*;
import com.checa.crm.pojo.entity.SysUser;
import com.checa.crm.pojo.vo.AuthVos.*;
import com.checa.crm.security.AuthInterceptor;
import com.checa.crm.security.JwtTokenService;
import com.checa.crm.util.PasswordUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {
    private final SysUserMapper users; private final SmsService sms; private final JwtTokenService jwt; private final StringRedisTemplate redis;
    public AuthService(SysUserMapper users,SmsService sms,JwtTokenService jwt,StringRedisTemplate redis){this.users=users;this.sms=sms;this.jwt=jwt;this.redis=redis;}
    public SessionVO login(LoginRequest req,String ip){SysUser u=users.findByAccountOrPhone(req.account());if(u==null)throw new BusinessException(401,"账号或密码不正确");if(!PasswordUtil.matches(req.password(),u.getPassword()))throw new BusinessException(401,"账号或密码不正确");if(!req.phone().equals(u.getPhone()))throw new BusinessException(401,"手机号与账号不匹配");if(!"0".equals(u.getStatus()))throw new BusinessException(403,"该账号已停用");sms.verifyAndConsume(req.phone(),req.verificationCode());users.updateLogin(u.getUserId(),ip);return new SessionVO(jwt.issue(u.getUserId()),user(u));}
    @Transactional public SessionVO register(RegisterRequest req){if(req.confirmPassword()!=null&&!req.confirmPassword().isBlank()&&!req.password().equals(req.confirmPassword()))throw new BusinessException("两次输入的密码不一致");sms.verifyAndConsume(req.phone(),req.verificationCode());if(users.countByPhone(req.phone())>0)throw new BusinessException("该手机号已注册");String account=req.account();if(users.countByUsername(account)>0)throw new BusinessException("账号已存在（含已注销账号），请更换账号");SysUser u=new SysUser();u.setUserId("u_"+UUID.randomUUID().toString().replace("-",""));u.setDeptId(req.deptId()==null||req.deptId().isBlank()?"d1":req.deptId());u.setUsername(account);u.setPassword(PasswordUtil.hash(req.password()));u.setRealName(req.realName()==null||req.realName().isBlank()?"新注册用户":req.realName());u.setPhone(req.phone());u.setEmail(req.email());u.setPosition(req.position()==null||req.position().isBlank()?"客户经理":req.position());u.setStatus("0");u.setHireDate(LocalDate.now());users.insert(u);users.bindRole(u.getUserId(),"r_sales");return new SessionVO(jwt.issue(u.getUserId()),user(u));}
    public UserVO me(String id){SysUser u=users.findById(id);if(u==null)throw new BusinessException(401,"用户不存在或已注销");if(!"0".equals(u.getStatus()))throw new BusinessException(403,"账号已停用");return user(u);}
    public void logout(String token){long ttl=24*60*60;redis.opsForValue().set("auth:revoked:"+AuthInterceptor.sha(token),"1",ttl, TimeUnit.SECONDS);}
    @Transactional public UserVO updateProfile(String id,ProfileUpdate req){SysUser u=users.findById(id);if(u==null)throw new BusinessException(401,"用户不存在或已注销");if(req.phone()!=null&&!req.phone().isBlank()){if(!req.phone().matches("^\\d{7,15}$"))throw new BusinessException("手机号格式不正确");if(users.countPhoneExcept(req.phone(),id)>0)throw new BusinessException("手机号已被其他账号使用");}users.updateProfile(id,req.name(),req.phone(),req.email());return user(users.findById(id));}
    @Transactional public void changePassword(String id,PasswordChange req){SysUser u=users.findById(id);if(u==null)throw new BusinessException(401,"用户不存在或已注销");if(!PasswordUtil.matches(req.oldPassword(),u.getPassword()))throw new BusinessException("当前密码不正确");if(req.oldPassword().equals(req.newPassword()))throw new BusinessException("新密码不能与当前密码相同");users.updatePassword(id,PasswordUtil.hash(req.newPassword()));}
    public JwtTokenService jwt(){return jwt;}
    private UserVO user(SysUser u){List<String> roles=users.roleNames(u.getUserId()),roleIds=users.roleIds(u.getUserId()),perms=users.permissions(u.getUserId());String dept=users.departmentName(u.getDeptId());String role=String.join(" / ",roles);return new UserVO(u.getUserId(),u.getRealName(),u.getUsername(),role,roles,roleIds,dept,u.getDeptId(),u.getPosition(),u.getPhone(),u.getEmail(),perms);}
}
