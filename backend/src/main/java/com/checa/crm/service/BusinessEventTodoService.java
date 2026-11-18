package com.checa.crm.service;

import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.resource.ReceivableAgingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 业务事件自动待办生成服务（方案 6.1 & 8.5）：
 * 1. 报价发出超过 3 天未反馈，自动生成「报价跟进」待办；
 * 2. 客户超过 30 天未联系，自动生成「休眠客户关怀」待办；
 * 3. 应收账款逾期，自动生成「回款催收」待办。
 * 具备幂等防重约束（同来源未完成待办不重复生成）。
 */
@Service
public class BusinessEventTodoService {
    private final GenericRecordMapper mapper;
    private final ReceivableAgingService agingService;
    private final ObjectMapper json;

    public BusinessEventTodoService(GenericRecordMapper mapper, ReceivableAgingService agingService, ObjectMapper json) {
        this.mapper = mapper;
        this.agingService = agingService;
        this.json = json;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledRun() {
        generateAutoTodos("system");
    }

    @Transactional
    public Map<String, Object> generateAutoTodos(String actor) {
        LocalDate today = LocalDate.now();
        int createdCount = 0;

        // 预加载现有待办，用于防重
        List<Map<String, Object>> existingTodos = listRecords("todos");
        Set<String> activeTodoKeys = new HashSet<>();
        for (var t : existingTodos) {
            String st = text(t.get("status"));
            if (!"已完成".equals(st) && !"已关闭".equals(st)) {
                String rType = text(t.get("relatedType"));
                String rId = text(t.get("relatedId"));
                String taskType = text(t.get("taskType"));
                if (rType != null && rId != null && taskType != null) {
                    activeTodoKeys.add(rType + ":" + rId + ":" + taskType);
                }
            }
        }

        // 1. 扫描报价（超 3 天未反馈）
        List<Map<String, Object>> quotes = listRecords("quotes");
        for (var q : quotes) {
            String st = text(q.get("status"));
            if ("已完成".equals(st) || "已接受".equals(st) || "已转订单".equals(st) || "已失效".equals(st) || "已拒绝".equals(st)) continue;
            String created = text(q.get("createdAt"));
            LocalDate createDate = parseDate(created);
            if (createDate != null && ChronoUnit.DAYS.between(createDate, today) >= 3) {
                String qId = text(q.get("id"));
                String key = "报价:" + qId + ":报价跟进";
                if (!activeTodoKeys.contains(key)) {
                    String cust = text(q.get("customer"));
                    String qNo = text(q.get("quoteNo"));
                    createTodo(actor,
                            "报价跟进：【" + (cust != null ? cust : "客户") + "】报价已超3天未反馈",
                            "报价单 " + (qNo != null ? qNo : qId) + " 已发出超 3 天，请及时跟进客户决策进展",
                            qId, "报价", "报价跟进",
                            cust, text(q.get("owner")),
                            today.plusDays(1).toString(), "高",
                            text(q.get("departmentId")));
                    activeTodoKeys.add(key);
                    createdCount++;
                }
            }
        }

        // 1.5 扫描线索（待首次联系：已分配但超过 N 天仍未首次跟进）
        List<Map<String, Object>> leads = listRecords("leads");
        for (var lead : leads) {
            String stage = text(lead.get("stage"));
            if (!"待分配".equals(stage) && !"已分配".equals(stage)) continue;
            String created = text(lead.get("createdAt"));
            LocalDate createDate = parseDate(created);
            if (createDate == null || ChronoUnit.DAYS.between(createDate, today) < 3) continue;
            String leadId = text(lead.get("id"));
            String key = "线索:" + leadId + ":首次联系";
            if (!activeTodoKeys.contains(key)) {
                String leadName = text(lead.get("name"));
                String owner = text(lead.get("owner"));
                if (owner == null || owner.isBlank()) owner = "待分配";
                createTodo(actor,
                        "线索待首次联系：【" + (leadName != null ? leadName : "线索") + "】已分配超 3 天未联系",
                        "线索已分配 " + ChronoUnit.DAYS.between(createDate, today) + " 天仍未首次联系，请尽快触达",
                        leadId, "线索", "首次联系",
                        text(lead.get("company")), owner,
                        today.plusDays(1).toString(), "高",
                        text(lead.get("departmentId")));
                activeTodoKeys.add(key);
                createdCount++;
            }
        }

        // 2. 扫描客户（超 30 天未联系）
        List<Map<String, Object>> customers = listRecords("customers");
        for (var c : customers) {
            String owner = text(c.get("owner"));
            if (owner == null || owner.isBlank()) continue; // 公海客户不分配个人待办
            String last = text(c.get("lastContact"));
            if (last == null) last = text(c.get("createdAt"));
            LocalDate lastDate = parseDate(last);
            if (lastDate != null && ChronoUnit.DAYS.between(lastDate, today) >= 30) {
                String cId = text(c.get("id"));
                String key = "客户:" + cId + ":客户关怀";
                if (!activeTodoKeys.contains(key)) {
                    String cName = text(c.get("name"));
                    createTodo(actor,
                            "休眠客户关怀：【" + (cName != null ? cName : "客户") + "】超30天未联系",
                            "该客户已超过 30 天无新跟进记录，建议致电或上门拜访，维护客户关系",
                            cId, "客户", "客户关怀",
                            cName, owner,
                            today.plusDays(2).toString(), "中",
                            text(c.get("departmentId")));
                    activeTodoKeys.add(key);
                    createdCount++;
                }
            }
        }

        // 3. 扫描应收（逾期应收催收）
        List<Map<String, Object>> receivables = listRecords("receivables");
        for (var ar : receivables) {
            agingService.recalculate(ar);
            if (Boolean.TRUE.equals(ar.get("isOverdue"))) {
                String arId = text(ar.get("id"));
                String key = "应收:" + arId + ":回款催收";
                if (!activeTodoKeys.contains(key)) {
                    String cust = text(ar.get("customer"));
                    String orderNo = text(ar.get("orderNo"));
                    createTodo(actor,
                            "回款催收：【" + (cust != null ? cust : "客户") + "】账单已逾期",
                            "订单 " + (orderNo != null ? orderNo : arId) + " 欠款已逾期 " + ar.get("agingDays") + " 天，待回款 ¥" + ar.get("outstanding") + "，请及时催收",
                            arId, "应收", "回款催收",
                            cust, text(ar.get("owner")),
                            today.plusDays(1).toString(), "高",
                            text(ar.get("departmentId")));
                    activeTodoKeys.add(key);
                    createdCount++;
                }
            }
        }

        return Map.of("createdCount", createdCount, "message", "已成功自动生成 " + createdCount + " 条业务待办任务");
    }

    private void createTodo(String actor, String title, String related, String relatedId, String relatedType, String taskType, String customer, String owner, String dueDate, String priority, String departmentId) {
        String id = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("title", title);
        payload.put("related", related);
        payload.put("relatedId", relatedId);
        payload.put("relatedType", relatedType);
        payload.put("taskType", taskType);
        payload.put("customer", customer);
        payload.put("owner", owner);
        payload.put("dueDate", dueDate);
        payload.put("priority", priority);
        payload.put("status", "待处理");
        payload.put("departmentId", departmentId);
        payload.put("createdAt", LocalDate.now().toString());

        try {
            String jsonStr = json.writeValueAsString(payload);
            mapper.insert(id, "todos", jsonStr, departmentId, null, "待处理", actor);
        } catch (Exception ignored) {}
    }

    private List<Map<String, Object>> listRecords(String key) {
        var rows = mapper.page(key, null, null, null, null, null, false, null, Map.of(), null, "desc", 0, 5000);
        List<Map<String, Object>> list = new ArrayList<>();
        for (var row : rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            try {
                if (row.get("payload") != null) {
                    record.putAll(json.readValue(String.valueOf(row.get("payload")), new TypeReference<Map<String, Object>>() {}));
                }
            } catch (Exception ignored) {}
            record.put("id", row.get("id"));
            if (row.get("departmentId") != null) record.put("departmentId", row.get("departmentId"));
            list.add(record);
        }
        return list;
    }

    private static String text(Object val) {
        if (val == null) return null;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate parseDate(String val) {
        if (val == null) return null;
        try {
            return LocalDate.parse(val.length() >= 10 ? val.substring(0, 10) : val);
        } catch (Exception e) {
            return null;
        }
    }
}
