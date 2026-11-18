package com.checa.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloopen.sms")
public class CloopenSmsProperties {
    private String serverIp;
    private String serverPort;
    private String accountSid;
    private String accountToken;
    private String appId;
    private String templateId;
    private String expireMinutes;
    private String defaultPhone;

    public String getServerIp() { return serverIp; }
    public void setServerIp(String serverIp) { this.serverIp = serverIp; }
    public String getServerPort() { return serverPort; }
    public void setServerPort(String serverPort) { this.serverPort = serverPort; }
    public String getAccountSid() { return accountSid; }
    public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
    public String getAccountToken() { return accountToken; }
    public void setAccountToken(String accountToken) { this.accountToken = accountToken; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getExpireMinutes() { return expireMinutes; }
    public void setExpireMinutes(String expireMinutes) { this.expireMinutes = expireMinutes; }
    public String getDefaultPhone() { return defaultPhone; }
    public void setDefaultPhone(String defaultPhone) { this.defaultPhone = defaultPhone; }
}
