package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.ConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 系统参数：公海联系方式脱敏开关、长期未跟进自动回收配置。
 */
@Service
public class ConfigService {
    public static final String POOL_CONTACT_FULL = "pool.contact.full";
    public static final String POOL_AUTO_ENABLED = "pool.auto.recycle.enabled";
    public static final String POOL_AUTO_DAYS = "pool.auto.recycle.days";
    public static final String POOL_AUTO_RESOURCES = "pool.auto.recycle.resources";
    private static final Set<String> WRITABLE = Set.of(POOL_CONTACT_FULL, POOL_AUTO_ENABLED, POOL_AUTO_DAYS, POOL_AUTO_RESOURCES);

    private final ConfigMapper mapper;

    public ConfigService(ConfigMapper mapper) { this.mapper = mapper; }

    public String value(String key) { return mapper.value(key); }

    public boolean bool(String key, boolean fallback) {
        String value = mapper.value(key);
        if (value == null || value.isBlank()) return fallback;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public int integer(String key, int fallback) {
        String value = mapper.value(key);
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        for (var row : mapper.all()) out.put(String.valueOf(row.get("configKey")), row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        return out;
    }

    @Transactional
    public void save(String actor, Map<String, String> values) {
        if (values == null || values.isEmpty()) throw new BusinessException("没有需要保存的参数");
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!WRITABLE.contains(key)) throw new BusinessException("不支持的系统参数：" + key);
            switch (key) {
                case POOL_CONTACT_FULL, POOL_AUTO_ENABLED -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) throw new BusinessException("开关参数只能是 true 或 false");
                }
                case POOL_AUTO_DAYS -> {
                    try {
                        int days = Integer.parseInt(value);
                        if (days < 1 || days > 365) throw new BusinessException("自动回收天数需在 1–365 之间");
                    } catch (NumberFormatException e) { throw new BusinessException("自动回收天数必须是数字"); }
                }
                case POOL_AUTO_RESOURCES -> {
                    if (value.isBlank()) throw new BusinessException("请选择自动回收的资源");
                    for (String token : value.split(",")) {
                        String t = token.trim();
                        if (!"leads".equals(t) && !"customers".equals(t)) throw new BusinessException("自动回收资源只能是 leads 或 customers");
                    }
                }
                default -> { }
            }
            mapper.upsert(key, value, actor);
        }
    }
}
