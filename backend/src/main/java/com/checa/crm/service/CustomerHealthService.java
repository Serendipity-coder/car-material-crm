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
 * 客户分层与智能健康度计算引擎（方案 6.13 & 8.5）：
 * 依据客户订单履约周期、历史消费、售后投诉、逾期欠款及跟进频次，
 * 自动计算客户健康等级（健康、亚健康、流失预警、濒危高危、高价值VIP）与价值分层（S/A/B/C/D）。
 */
@Service
public class CustomerHealthService {
    private final GenericRecordMapper mapper;
    private final ReceivableAgingService agingService;
    private final ObjectMapper json;

    public CustomerHealthService(GenericRecordMapper mapper, ReceivableAgingService agingService, ObjectMapper json) {
        this.mapper = mapper;
        this.agingService = agingService;
        this.json = json;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void scheduledEvaluation() {
        evaluateAllCustomers("system");
    }

    @Transactional
    public Map<String, Object> evaluateAllCustomers(String actor) {
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> customers = listRecords("customers");
        List<Map<String, Object>> orders = listRecords("orders");
        List<Map<String, Object>> followups = listRecords("followups");
        List<Map<String, Object>> tickets = listRecords("service-tickets");
        List<Map<String, Object>> receivables = listRecords("receivables");

        // 按客户汇总订单
        Map<String, List<Map<String, Object>>> ordersByCustomer = new HashMap<>();
        for (var o : orders) {
            String cust = text(o.get("customerId") != null ? o.get("customerId") : o.get("customer"));
            if (cust != null) ordersByCustomer.computeIfAbsent(cust, k -> new ArrayList<>()).add(o);
        }

        // 按客户汇总跟进
        Map<String, LocalDate> lastContactByCustomer = new HashMap<>();
        for (var f : followups) {
            String cust = text(f.get("customerId") != null ? f.get("customerId") : f.get("customer"));
            LocalDate date = parseDate(text(f.get("date") != null ? f.get("date") : f.get("createdAt")));
            if (cust != null && date != null) {
                if (!lastContactByCustomer.containsKey(cust) || date.isAfter(lastContactByCustomer.get(cust))) {
                    lastContactByCustomer.put(cust, date);
                }
            }
        }

        // 按客户汇总未解决投诉
        Map<String, Integer> complaintsByCustomer = new HashMap<>();
        for (var tk : tickets) {
            String st = text(tk.get("status"));
            if (!"已关闭".equals(st) && !"已解决".equals(st)) {
                String cust = text(tk.get("customerId") != null ? tk.get("customerId") : tk.get("customer"));
                if (cust != null) complaintsByCustomer.put(cust, complaintsByCustomer.getOrDefault(cust, 0) + 1);
            }
        }

        // 按客户汇总逾期账单
        Map<String, Integer> maxOverdueDaysByCustomer = new HashMap<>();
        for (var ar : receivables) {
            agingService.recalculate(ar);
            if (Boolean.TRUE.equals(ar.get("isOverdue"))) {
                String cust = text(ar.get("customerId") != null ? ar.get("customerId") : ar.get("customer"));
                int days = ar.get("agingDays") instanceof Number n ? n.intValue() : 0;
                if (cust != null) {
                    maxOverdueDaysByCustomer.put(cust, Math.max(maxOverdueDaysByCustomer.getOrDefault(cust, 0), days));
                }
            }
        }

        int updatedCount = 0;
        Map<String, Integer> distribution = new LinkedHashMap<>();

        for (var cust : customers) {
            String cId = text(cust.get("id"));
            String cName = text(cust.get("name"));
            String matchKey = cId != null ? cId : cName;

            // 1. 最近订单与累计金额
            List<Map<String, Object>> myOrders = ordersByCustomer.getOrDefault(matchKey, ordersByCustomer.getOrDefault(cName, List.of()));
            BigDecimal annualSpend = BigDecimal.ZERO;
            LocalDate lastOrderDate = null;
            for (var o : myOrders) {
                BigDecimal amt = decimal(o.get("amount"));
                if (amt != null) annualSpend = annualSpend.add(amt);
                LocalDate oDate = parseDate(text(o.get("orderDate") != null ? o.get("orderDate") : o.get("createdAt")));
                if (oDate != null && (lastOrderDate == null || oDate.isAfter(lastOrderDate))) {
                    lastOrderDate = oDate;
                }
            }

            // 2. 最近联系
            LocalDate lastContact = lastContactByCustomer.get(matchKey);
            if (lastContact == null) lastContact = lastContactByCustomer.get(cName);
            if (lastContact == null && lastOrderDate != null) lastContact = lastOrderDate;
            if (lastContact == null) lastContact = parseDate(text(cust.get("createdAt")));

            long daysSinceOrder = lastOrderDate != null ? ChronoUnit.DAYS.between(lastOrderDate, today) : 999;
            long daysSinceContact = lastContact != null ? ChronoUnit.DAYS.between(lastContact, today) : 999;
            int maxOverdue = maxOverdueDaysByCustomer.getOrDefault(matchKey, maxOverdueDaysByCustomer.getOrDefault(cName, 0));
            int openComplaints = complaintsByCustomer.getOrDefault(matchKey, complaintsByCustomer.getOrDefault(cName, 0));

            // 3. 计算健康度 (healthStatus)
            String healthStatus;
            String healthReason;
            if (maxOverdue >= 30 || openComplaints >= 2) {
                healthStatus = "高危预警";
                healthReason = maxOverdue >= 30 ? "存在严重逾期欠款超 " + maxOverdue + " 天" : "存在 " + openComplaints + " 起未解决售后投诉";
            } else if (daysSinceOrder >= 60 || daysSinceContact >= 45) {
                healthStatus = "流失预警";
                healthReason = daysSinceOrder >= 60 ? "超 " + daysSinceOrder + " 天无新订单" : "超 " + daysSinceContact + " 天无有效联系";
            } else if (daysSinceOrder >= 30 || openComplaints == 1) {
                healthStatus = "亚健康";
                healthReason = openComplaints == 1 ? "存在未完结售后工单" : "采购频次放缓（距上次订单 " + daysSinceOrder + " 天）";
            } else if (annualSpend.compareTo(BigDecimal.valueOf(100000)) >= 0) {
                healthStatus = "高价值客户";
                healthReason = "累计采购额达 ¥" + annualSpend + "，合作活跃履约良好";
            } else {
                healthStatus = "正常";
                healthReason = "采购与跟进频次正常";
            }

            // 4. 计算客户分层 (customerLevel)
            String level;
            if (annualSpend.compareTo(BigDecimal.valueOf(200000)) >= 0) level = "S级战略客户";
            else if (annualSpend.compareTo(BigDecimal.valueOf(100000)) >= 0) level = "A级核心客户";
            else if (annualSpend.compareTo(BigDecimal.valueOf(50000)) >= 0) level = "B级重要客户";
            else if (annualSpend.compareTo(BigDecimal.valueOf(10000)) >= 0) level = "C级普通客户";
            else level = "D级零散客户";

            distribution.put(healthStatus, distribution.getOrDefault(healthStatus, 0) + 1);

            // 更新 customer payload 与镜像表
            boolean changed = !Objects.equals(healthStatus, cust.get("healthStatus")) || !Objects.equals(level, cust.get("level"));
            if (changed) {
                cust.put("healthStatus", healthStatus);
                cust.put("healthReason", healthReason);
                cust.put("level", level);
                cust.put("annualSpend", annualSpend);
                if (lastOrderDate != null) cust.put("lastOrderDate", lastOrderDate.toString());
                if (lastContact != null) cust.put("lastContact", lastContact.toString());

                try {
                    String dept = String.valueOf(cust.get("departmentId"));
                    mapper.update(cId, json.writeValueAsString(cust), dept, null, text(cust.get("status")), actor);
                    mapper.mirrorCustomer(cId, dept, cName, level, text(cust.get("status")));
                    updatedCount++;
                } catch (Exception ignored) {}
            }
        }

        return Map.of(
                "evaluatedTotal", customers.size(),
                "updatedCount", updatedCount,
                "distribution", distribution,
                "message", "已完成 " + customers.size() + " 家客户的健康度与分层智能评估，更新 " + updatedCount + " 家客户状态"
        );
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

    private static BigDecimal decimal(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return new BigDecimal(n.toString());
        try {
            String s = String.valueOf(val).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
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
