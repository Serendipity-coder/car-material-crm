package com.checa.crm.service;

import com.checa.crm.config.CloopenSmsProperties;
import com.cloopen.rest.sdk.BodyType;
import com.cloopen.rest.sdk.CCPRestSmsSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;

@Component
public class CloopenSmsClient {
    private static final Logger log = LoggerFactory.getLogger(CloopenSmsClient.class);
    private final CloopenSmsProperties properties;
    private CCPRestSmsSDK sdk;

    public CloopenSmsClient(CloopenSmsProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        sdk = new CCPRestSmsSDK();
        sdk.init(properties.getServerIp(), properties.getServerPort());
        sdk.setAccount(properties.getAccountSid(), properties.getAccountToken());
        sdk.setAppId(properties.getAppId());
        sdk.setBodyType(BodyType.Type_JSON);
    }

    public boolean sendTemplateSms(String phone, String[] data) {
        try {
            HashMap<String, Object> result = sdk.sendTemplateSMS(phone, properties.getTemplateId(), data);
            if ("000000".equals(String.valueOf(result.get("statusCode")))) {
                log.info("容联云短信发送成功，手机号={}", phone);
                return true;
            }
            log.error("容联云短信发送失败，statusCode={}, statusMsg={}", result.get("statusCode"), result.get("statusMsg"));
            return false;
        } catch (Exception ex) {
            log.error("调用容联云短信接口异常", ex);
            return false;
        }
    }
}
