package com.checa.crm.security;

import com.checa.crm.exception.BusinessException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

public class JwtTokenService {
    private final String secret; private final long expireSeconds;
    public JwtTokenService(String secret,long expireSeconds){this.secret=secret;this.expireSeconds=expireSeconds;}
    public String issue(String subject){long exp=Instant.now().getEpochSecond()+expireSeconds;String h=b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"),p=b64("{\"sub\":\""+escape(subject)+"\",\"exp\":"+exp+"}");return h+"."+p+"."+sign(h+"."+p);}
    public String subject(String token){try{String[] p=token.split("\\.");if(p.length!=3||!constant(sign(p[0]+"."+p[1]),p[2]))throw new IllegalArgumentException();String body=new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8);var m=Pattern.compile("\\\"sub\\\":\\\"([^\\\"]+)\\\".*?\\\"exp\\\":(\\d+)").matcher(body);if(!m.find()||Long.parseLong(m.group(2))<Instant.now().getEpochSecond())throw new IllegalArgumentException();return m.group(1);}catch(Exception e){throw new BusinessException(401,"登录会话无效或已过期");}}
    private String sign(String s){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean constant(String a,String b){if(a.length()!=b.length())return false;int d=0;for(int i=0;i<a.length();i++)d|=a.charAt(i)^b.charAt(i);return d==0;}
    private static String b64(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}
