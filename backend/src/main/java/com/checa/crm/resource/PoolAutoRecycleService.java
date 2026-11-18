package com.checa.crm.resource;

import com.checa.crm.mapper.SysUserMapper;
import com.checa.crm.pojo.entity.SysUser;
import com.checa.crm.security.OrgPermissionGuard;
import com.checa.crm.service.ConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 长期未跟进的线索 / 客户自动回收。
 * <p>每天 02:30 按配置执行一次，也可由主管/管理员手动触发（用于验证与临时处理）。
 * 判定口径：取 {@code lastContact}，没有则取 {@code nextDate}，再没有取 {@code createdAt}；
 * 早于「今天 - N 天」且仍有负责人的记录会被放回公海，并写入 {@code crm_pool_log}。</p>
 */
@Service
public class PoolAutoRecycleService {
    private static final Logger log = LoggerFactory.getLogger(PoolAutoRecycleService.class);
    private static final Set<String> CLOSED_STATUS = Set.of("转化成功", "已完成", "待分配", "待认领", "公海", "无效", "已关闭");
    private static final int MAX_ROWS = 5000;

    private final GenericRecordMapper records;
    private final PoolMapper pool;
    private final PoolService poolService;
    private final SysUserMapper users;
    private final ConfigService config;
    private final OrgPermissionGuard guard;
    private final ObjectMapper json;

    public PoolAutoRecycleService(GenericRecordMapper records, PoolMapper pool, PoolService poolService,
                                  SysUserMapper users, ConfigService config, OrgPermissionGuard guard, ObjectMapper json) {
        this.records = records; this.pool = pool; this.poolService = poolService;
        this.users = users; this.config = config; this.guard = guard; this.json = json;
    }

    /** 每日 02:30 自动执行。 */
    @Scheduled(cron = "0 30 2 * * ?")
    public void scheduledRecycle() {
        try {
            if (!config.bool(ConfigService.POOL_AUTO_ENABLED, true)) return;
            int days = config.integer(ConfigService.POOL_AUTO_DAYS, 30);
            var result = run(days, null, "系统自动回收");
            log.info("公海自动回收完成：{}", result);
        } catch (Exception e) {
            log.warn("公海自动回收失败：{}", e.getMessage(), e);
        }
    }

    /** 手动触发（主管/管理员），days 为空时使用系统配置。 */
    public Map<String, Object> manual(String actor, Integer days) {
        guard.require(actor, "crm:pool:assign");
        SysUser operator = users.findById(actor);
        int value = days != null && days > 0 ? days : config.integer(ConfigService.POOL_AUTO_DAYS, 30);
        return run(value, actor, operator == null ? "系统自动回收" : operator.getRealName());
    }

    Map<String, Object> run(int days, String operatorId, String operatorName) {
        LocalDate threshold = LocalDate.now().minusDays(days);
        int leads = recycleLeads(threshold, days, operatorId, operatorName);
        int customers = 0;
        String resources = config.value(ConfigService.POOL_AUTO_RESOURCES);
        if (resources != null && resources.contains("customers")) customers = recycleCustomers(threshold, days, operatorId, operatorName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("threshold", threshold.toString());
        result.put("leads", leads);
        result.put("customers", customers);
        return result;
    }

    private int recycleLeads(LocalDate threshold, int days, String operatorId, String operatorName) {
        int count = 0;
        for (var row : records.page("leads", null, null, null, null, null, false, null, Map.of(), null, "desc", 0, MAX_ROWS)) {
            Map<String, Object> payload = read(row.get("payload"));
            String owner = text(payload.get("owner"));
            if (owner == null) continue;
            String status = text(payload.get("status"));
            if (status != null && CLOSED_STATUS.contains(status)) continue;
            LocalDate last = lastActive(payload);
            if (last == null || last.isAfter(threshold)) continue;
            String id = String.valueOf(row.get("id"));
            pool.release(id, "leads", operatorId);
            pool.log(id, "leads", "AUTO_RECYCLE", owner, null, operatorId, operatorName, "超过 " + days + " 天未跟进，系统自动回收");
            count++;
        }
        return count;
    }

    private int recycleCustomers(LocalDate threshold, int days, String operatorId, String operatorName) {
        int count = 0;
        for (var row : records.page("customers", null, null, null, null, null, false, null, Map.of(), null, "desc", 0, MAX_ROWS)) {
            Map<String, Object> payload = read(row.get("payload"));
            if (text(payload.get("owner")) == null) continue;
            LocalDate last = lastActive(payload);
            if (last == null || last.isAfter(threshold)) continue;
            try {
                String id = poolService.recycleCustomerInternal(String.valueOf(row.get("id")), operatorId, operatorName,
                        "AUTO_RECYCLE", "超过 " + days + " 天未维护，自动回收：");
                if (id != null) count++;
            } catch (Exception e) {
                log.debug("客户自动回收跳过 {}：{}", row.get("id"), e.getMessage());
            }
        }
        return count;
    }

    private LocalDate lastActive(Map<String, Object> payload) {
        for (String field : List.of("lastContact", "nextDate", "createdAt")) {
            LocalDate date = parse(text(payload.get(field)));
            if (date != null) return date;
        }
        return null;
    }

    private static LocalDate parse(String value) {
        if (value == null) return null;
        try { return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value); } catch (Exception e) { return null; }
    }

    private Map<String, Object> read(Object payload) {
        try {
            return json.readValue(String.valueOf(payload), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String text(Object value) {
        if (value == null) return null;
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }
}
