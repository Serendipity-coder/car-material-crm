package com.checa.crm.util;

import java.security.SecureRandom;

public final class CodeUtil {
    private static final SecureRandom RANDOM=new SecureRandom();
    private CodeUtil(){}
    public static String fourDigits(){return String.format("%04d",RANDOM.nextInt(10000));}
}
