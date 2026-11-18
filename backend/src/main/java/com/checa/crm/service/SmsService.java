package com.checa.crm.service;

import com.checa.crm.config.CloopenSmsProperties;
import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.SysUser;
import com.checa.crm.pojo.vo.AuthVos.CodeVO;
import com.checa.crm.util.CodeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class SmsService {
    private static final String PREFIX="sms:code:"; private static final long TTL=120;
    private final StringRedisTemplate redis; private final SysUserMapper users; private final CloopenSmsClient smsClient; private final CloopenSmsProperties smsProperties; private final boolean devMode;
    public SmsService(StringRedisTemplate redis,SysUserMapper users,CloopenSmsClient smsClient,CloopenSmsProperties smsProperties,@Value("${app.dev-mode:true}") boolean devMode){this.redis=redis;this.users=users;this.smsClient=smsClient;this.smsProperties=smsProperties;this.devMode=devMode;}
    public CodeVO sendLoginCode(String account,String phone){SysUser u=users.findByAccountOrPhone(account);if(u==null)throw new BusinessException(404,"账号不存在");if(!phone.equals(u.getPhone()))throw new BusinessException("手机号与账号不匹配");if(!"0".equals(u.getStatus()))throw new BusinessException("该账号已停用");return save(phone);}
    public CodeVO sendRegisterCode(String phone){if(users.countByPhone(phone)>0)throw new BusinessException("该手机号已注册");return save(phone);}
    public void verifyAndConsume(String phone,String code){String key=PREFIX+phone;String expected=redis.opsForValue().get(key);if(expected==null)throw new BusinessException("验证码已过期，请重新获取");if(!expected.equals(code)){Long n=redis.opsForValue().increment("sms:fail:"+phone);if(n!=null&&n>=5){redis.delete(key);redis.delete("sms:fail:"+phone);}throw new BusinessException("验证码不正确");}redis.delete(key);redis.delete("sms:fail:"+phone);}
    private CodeVO save(String phone){String code=CodeUtil.fourDigits();String expireMinutes=smsProperties.getExpireMinutes()==null?"2":smsProperties.getExpireMinutes();String[] data={code,expireMinutes};String targetPhone=smsProperties.getDefaultPhone()==null||smsProperties.getDefaultPhone().isBlank()?phone:smsProperties.getDefaultPhone();if(!smsClient.sendTemplateSms(targetPhone,data))throw new BusinessException(503,"短信验证码发送失败，请检查容联云配置、模板和账户状态");redis.opsForValue().set(PREFIX+phone,code,TTL, TimeUnit.SECONDS);return new CodeVO("验证码已发送",devMode?code:null,TTL);}
}
