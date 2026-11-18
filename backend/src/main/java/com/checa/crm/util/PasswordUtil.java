package com.checa.crm.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PasswordUtil {
    private static final int ITERATIONS=210000, KEY_BITS=256;
    private static final SecureRandom RANDOM=new SecureRandom();
    private PasswordUtil(){}
    public static String hash(String raw){
        try{byte[] salt=new byte[16];RANDOM.nextBytes(salt);byte[] out=derive(raw,salt,ITERATIONS);return "pbkdf2$"+ITERATIONS+"$"+HexFormat.of().formatHex(salt)+"$"+HexFormat.of().formatHex(out);}catch(Exception e){throw new IllegalStateException("密码加密失败",e);}
    }
    public static boolean matches(String raw,String encoded){
        try{if(encoded==null||!encoded.startsWith("pbkdf2$"))return false;String[] p=encoded.split("\\$",-1);if(p.length!=4)return false;int it=Integer.parseInt(p[1]);if(it<100000||it>1000000)return false;byte[] salt=HexFormat.of().parseHex(p[2]);byte[] expected=HexFormat.of().parseHex(p[3]);return MessageDigest.isEqual(expected,derive(raw,salt,it));}catch(Exception e){return false;}
    }
    private static byte[] derive(String raw,byte[] salt,int iterations)throws Exception{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(raw.toCharArray(),salt,iterations,KEY_BITS)).getEncoded();}
}
