package com.checa.crm.config;

import com.checa.crm.security.AuthInterceptor;
import com.checa.crm.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SecurityConfig {
    @Bean
    public JwtTokenService jwtTokenService(@Value("${app.jwt-secret}") String secret,@Value("${app.jwt-expire-seconds:86400}") long seconds){return new JwtTokenService(secret,seconds);}
    @Bean
    public AuthInterceptor authInterceptor(JwtTokenService jwt,StringRedisTemplate redis,com.checa.crm.mapper.SysUserMapper users){return new AuthInterceptor(jwt,redis,users);}
}
