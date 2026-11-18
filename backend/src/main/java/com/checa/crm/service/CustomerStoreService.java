package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.security.OrgPermissionGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 客户门店子实体服务（方案 6.3 & 8.7）：
 * 支持客户 1:N 独立门店建模，包含门店名称、类型、地址、工位数、联系人与状态维护。
 */
@Service
public class CustomerStoreService {
    private final GenericRecordMapper mapper;
    private final OrgPermissionGuard guard;
    private final ObjectMapper json;

    public CustomerStoreService(GenericRecordMapper mapper, OrgPermissionGuard guard, ObjectMapper json) {
        this.mapper = mapper;
        this.guard = guard;
        this.json = json;
    }

    public List<Map<String, Object>> listStores(String actor, String customerId) {
        guard.require(actor, "crm:customer:list");
        Map<String, Object> customerRow = mapper.find(customerId);
        if (customerRow == null) throw new BusinessException(404, "客户不存在");
        Map<String, Object> customerPayload = readPayload(customerRow);

        List<Map<String, Object>> rows = mapper.listByCustomerId("stores", customerId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (var row : rows) {
            Map<String, Object> record = readPayload(row);
            record.put("id", row.get("id"));
            record.put("departmentId", row.get("departmentId"));
            list.add(record);
        }

        // 如果该客户尚未建立独立门店记录，但档案上有 storeName，自动兼容为一条默认总店
        if (list.isEmpty()) {
            String legacyStore = text(customerPayload.get("storeName"));
            if (legacyStore != null && !legacyStore.isBlank()) {
                Map<String, Object> defaultStore = new LinkedHashMap<>();
                defaultStore.put("id", "st_default_" + customerId);
                defaultStore.put("customerId", customerId);
                defaultStore.put("customerName", text(customerPayload.get("name")));
                defaultStore.put("storeName", legacyStore);
                defaultStore.put("storeType", text(customerPayload.get("customerType") != null ? customerPayload.get("customerType") : "综合汽修厂"));
                defaultStore.put("region", text(customerPayload.get("region")));
                defaultStore.put("address", text(customerPayload.get("address")));
                defaultStore.put("contactName", text(customerPayload.get("contact")));
                defaultStore.put("contactPhone", text(customerPayload.get("phone")));
                defaultStore.put("status", "营业中");
                defaultStore.put("isDefault", true);
                list.add(defaultStore);
            }
        }

        return list;
    }

    @Transactional
    public Map<String, Object> createStore(String actor, String customerId, Map<String, Object> body) {
        guard.require(actor, "crm:customer:edit");
        if (body == null || text(body.get("storeName")) == null) {
            throw new BusinessException("门店名称不能为空");
        }
        Map<String, Object> customerRow = mapper.find(customerId);
        if (customerRow == null) throw new BusinessException(404, "客户不存在");
        Map<String, Object> customerPayload = readPayload(customerRow);

        String id = "st_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("id", id);
        payload.put("customerId", customerId);
        payload.put("customerName", text(customerPayload.get("name")));
        if (payload.get("status") == null) payload.put("status", "营业中");
        if (payload.get("createdAt") == null) payload.put("createdAt", LocalDate.now().toString());
        String dept = String.valueOf(customerRow.get("departmentId"));
        payload.put("departmentId", dept);

        try {
            mapper.insert(id, "stores", json.writeValueAsString(payload), dept, null, text(payload.get("status")), actor);
        } catch (Exception e) {
            throw new BusinessException("创建门店失败");
        }
        return payload;
    }

    @Transactional
    public Map<String, Object> updateStore(String actor, String customerId, String storeId, Map<String, Object> body) {
        guard.require(actor, "crm:customer:edit");
        Map<String, Object> row = mapper.find(storeId);
        if (row == null) throw new BusinessException(404, "门店不存在");
        Map<String, Object> payload = readPayload(row);
        if (body != null) {
            body.forEach((k, v) -> { if (v != null) payload.put(k, v); });
        }
        payload.put("id", storeId);
        payload.put("customerId", customerId);

        try {
            mapper.update(storeId, json.writeValueAsString(payload), String.valueOf(row.get("departmentId")), null, text(payload.get("status")), actor);
        } catch (Exception e) {
            throw new BusinessException("更新门店失败");
        }
        return payload;
    }

    @Transactional
    public void deleteStore(String actor, String customerId, String storeId) {
        guard.require(actor, "crm:customer:edit");
        Map<String, Object> row = mapper.find(storeId);
        if (row != null) {
            mapper.softDelete(storeId, actor);
        }
    }

    private Map<String, Object> readPayload(Map<String, Object> row) {
        try {
            if (row.get("payload") != null) {
                return json.readValue(String.valueOf(row.get("payload")), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    private static String text(Object val) {
        if (val == null) return null;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }
}
