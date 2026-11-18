package com.checa.crm.service;

import com.checa.crm.resource.GenericRecordMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 线索查重服务（方案 6.2 & 8.2）：
 * 提供独立的手机号、企业/门店名称查重接口，支持前端录入时实时校验与提示。
 */
@Service
public class LeadDuplicateService {
    private final GenericRecordMapper mapper;
    private final ObjectMapper json;

    public LeadDuplicateService(GenericRecordMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public Map<String, Object> checkDuplicate(String phone, String name, String excludeId) {
        List<Map<String, Object>> matches = new ArrayList<>();
        Set<String> matchedIds = new HashSet<>();

        if (phone != null && !phone.isBlank()) {
            String clean = phone.replaceAll("[^0-9]", "");
            if (clean.length() >= 7) {
                var byPhone = mapper.findByPhone(clean);
                for (var row : byPhone) {
                    String id = String.valueOf(row.get("id"));
                    if (id.equals(excludeId) || !matchedIds.add(id)) continue;
                    Map<String, Object> payload = readPayload(row);
                    matches.add(Map.of(
                            "resource", String.valueOf(row.get("resourceKey")),
                            "id", id,
                            "name", text(payload.get("name") != null ? payload.get("name") : payload.get("company")),
                            "phone", text(payload.get("phone")),
                            "owner", text(payload.get("owner")),
                            "matchField", "phone",
                            "reason", "手机号匹配重复"
                    ));
                }
            }
        }

        if (name != null && !name.isBlank()) {
            var byName = mapper.findByName(name.trim());
            for (var row : byName) {
                String id = String.valueOf(row.get("id"));
                if (id.equals(excludeId) || !matchedIds.add(id)) continue;
                Map<String, Object> payload = readPayload(row);
                matches.add(Map.of(
                        "resource", String.valueOf(row.get("resourceKey")),
                        "id", id,
                        "name", text(payload.get("name") != null ? payload.get("name") : payload.get("company")),
                        "phone", text(payload.get("phone")),
                        "owner", text(payload.get("owner")),
                        "matchField", "name",
                        "reason", "企业/门店名称匹配重复"
                ));
            }
        }

        return Map.of(
                "duplicate", !matches.isEmpty(),
                "totalMatches", matches.size(),
                "matches", matches
        );
    }

    private Map<String, Object> readPayload(Map<String, Object> row) {
        try {
            if (row.get("payload") != null) {
                return json.readValue(String.valueOf(row.get("payload")), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {}
        return Map.of();
    }

    private static String text(Object val) {
        if (val == null) return null;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }
}
