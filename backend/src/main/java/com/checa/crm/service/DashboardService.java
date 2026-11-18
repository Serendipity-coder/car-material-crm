package com.checa.crm.service;

import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.resource.ReceivableAgingService;
import com.checa.crm.security.DataScopeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 工作台服务端大盘汇总服务（方案 6.1）：
 * 服务端按照当前账号的数据范围计算经营指标，避免客户端大批量拉取明细。
 */
@Service
public class DashboardService {
    private final GenericRecordMapper mapper;
    private final DataScopeService dataScope;
    private final ReceivableAgingService agingService;
    private final ObjectMapper json;

    public DashboardService(GenericRecordMapper mapper, DataScopeService dataScope, ReceivableAgingService agingService, ObjectMapper json) {
        this.mapper = mapper;
        this.dataScope = dataScope;
        this.agingService = agingService;
        this.json = json;
    }

    public Map<String, Object> getOverview(String actor, String requestedDept) {
        var scope = dataScope.resolve(actor, "reports");
        List<String> deptIds = scope.all() ? (requestedDept != null && !requestedDept.isBlank() && !"__all__".equals(requestedDept) ? List.of(requestedDept) : null)
                : (scope.selfOnly() ? List.of() : scope.deptIds());

        LocalDate today = LocalDate.now();
        String currentMonth = YearMonth.now().toString(); // e.g. "2026-11"
        String currentYear = String.valueOf(today.getYear());

        // 1. 销售订单与销售额统计
        List<Map<String, Object>> orders = listRecords("orders", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        BigDecimal currentMonthSales = BigDecimal.ZERO;
        BigDecimal currentYearSales = BigDecimal.ZERO;
        Map<String, BigDecimal> monthSalesMap = new TreeMap<>();

        for (var rec : orders) {
            String date = text(rec.get("orderDate"));
            if (date == null) date = text(rec.get("createdAt"));
            BigDecimal amount = decimal(rec.get("amount"));
            if (amount == null) amount = BigDecimal.ZERO;

            if (date != null && date.startsWith(currentMonth)) {
                currentMonthSales = currentMonthSales.add(amount);
            }
            if (date != null && date.startsWith(currentYear)) {
                currentYearSales = currentYearSales.add(amount);
            }
            if (date != null && date.length() >= 7) {
                String ym = date.substring(0, 7);
                monthSalesMap.put(ym, monthSalesMap.getOrDefault(ym, BigDecimal.ZERO).add(amount));
            }
        }

        List<Map<String, Object>> monthlySales = new ArrayList<>();
        // 取最近 6 个月或当年月份
        for (var entry : monthSalesMap.entrySet()) {
            monthlySales.add(Map.of("month", entry.getKey(), "amount", entry.getValue()));
        }
        if (monthlySales.size() > 6) {
            monthlySales = monthlySales.subList(monthlySales.size() - 6, monthlySales.size());
        }

        // 2. 回款统计
        List<Map<String, Object>> payments = listRecords("payments", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        BigDecimal monthPaymentTotal = BigDecimal.ZERO;
        for (var rec : payments) {
            String date = text(rec.get("date"));
            if (date == null) date = text(rec.get("paymentDate"));
            if (date == null) date = text(rec.get("createdAt"));
            BigDecimal amount = decimal(rec.get("amount"));
            if (date != null && date.startsWith(currentMonth) && amount != null) {
                monthPaymentTotal = monthPaymentTotal.add(amount);
            }
        }

        // 3. 商机与加权预测
        List<Map<String, Object>> opps = listRecords("opportunities", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        int activeOppCount = 0;
        BigDecimal weightedOppAmount = BigDecimal.ZERO;
        for (var rec : opps) {
            String stage = text(rec.get("stage"));
            if ("赢单".equals(stage) || "输单".equals(stage)) continue;
            activeOppCount++;
            BigDecimal amount = decimal(rec.get("amount"));
            BigDecimal prob = decimal(rec.get("probability"));
            if (amount != null && prob != null) {
                BigDecimal weighted = amount.multiply(prob).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                weightedOppAmount = weightedOppAmount.add(weighted);
            }
        }

        // 4. 线索与来源占比
        List<Map<String, Object>> leads = listRecords("leads", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        int leadTotal = leads.size();
        Map<String, Integer> sourceCount = new LinkedHashMap<>();
        for (var rec : leads) {
            String source = text(rec.get("source"));
            if (source == null || source.isBlank()) source = "其他";
            sourceCount.put(source, sourceCount.getOrDefault(source, 0) + 1);
        }
        List<Map<String, Object>> sourceMix = new ArrayList<>();
        List<String> colors = List.of("#528a70", "#6ca188", "#88b79f", "#a5cdb8", "#c3a065", "#7a9e9f", "#909d94");
        int colorIdx = 0;
        for (var entry : sourceCount.entrySet()) {
            int val = entry.getValue();
            int percent = leadTotal > 0 ? (int) Math.round(val * 100.0 / leadTotal) : 0;
            sourceMix.add(Map.of(
                    "name", entry.getKey(),
                    "value", percent,
                    "count", val,
                    "color", colors.get(colorIdx % colors.size())
            ));
            colorIdx++;
        }

        // 5. 客户总数与健康度分布
        List<Map<String, Object>> customers = listRecords("customers", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        int customerTotal = customers.size();
        Map<String, Integer> healthCount = new LinkedHashMap<>();
        for (var rec : customers) {
            String hs = text(rec.get("healthStatus"));
            if (hs == null || hs.isBlank()) hs = "正常";
            healthCount.put(hs, healthCount.getOrDefault(hs, 0) + 1);
        }

        // 6. 待办任务统计（待处理、进行中、已完成、已逾期）
        List<Map<String, Object>> todos = listRecords("todos", null, actor, scope.userName());
        int todoPending = 0, todoCompleted = 0, todoOverdue = 0;
        for (var rec : todos) {
            String st = text(rec.get("status"));
            String due = text(rec.get("dueDate"));
            if ("已完成".equals(st)) {
                todoCompleted++;
            } else {
                todoPending++;
                if (due != null && !due.isBlank()) {
                    try {
                        if (LocalDate.parse(due).isBefore(today)) todoOverdue++;
                    } catch (Exception ignored) {}
                }
            }
        }

        // 7. 智能提醒列表 (Reminders)
        List<Map<String, Object>> reminders = new ArrayList<>();
        // (1) 逾期应收
        List<Map<String, Object>> receivables = listRecords("receivables", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        for (var rec : receivables) {
            agingService.recalculate(rec);
            if (Boolean.TRUE.equals(rec.get("isOverdue"))) {
                reminders.add(Map.of(
                        "id", "rem_ar_" + rec.get("id"),
                        "title", "应收逾期催收：" + text(rec.get("customer")),
                        "detail", "账单未结清，已逾期 " + rec.get("agingDays") + " 天，待收金额 ¥" + rec.get("outstanding"),
                        "due", String.valueOf(rec.get("dueDate")),
                        "route", "/receivables",
                        "kind", "overdue"
                ));
            }
        }
        // (2) 临期报价（还有 3 天到期）
        List<Map<String, Object>> quotes = listRecords("quotes", deptIds, actor, scope.selfOnly() ? scope.userName() : null);
        for (var rec : quotes) {
            String validUntil = text(rec.get("validUntil"));
            String st = text(rec.get("status"));
            if (validUntil != null && !"已接受".equals(st) && !"已转订单".equals(st) && !"已失效".equals(st)) {
                try {
                    LocalDate vu = LocalDate.parse(validUntil);
                    if (!vu.isBefore(today) && !vu.isAfter(today.plusDays(3))) {
                        reminders.add(Map.of(
                                "id", "rem_qt_" + rec.get("id"),
                                "title", "报价即将到期：" + text(rec.get("customer")),
                                "detail", "报价单 " + text(rec.get("quoteNo")) + " 将于 " + validUntil + " 到期，请尽快促成决策",
                                "due", validUntil,
                                "route", "/quotes",
                                "kind", "upcoming"
                        ));
                    }
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentMonthSales", currentMonthSales);
        result.put("currentYearSales", currentYearSales);
        result.put("monthlySales", monthlySales);
        result.put("monthPaymentTotal", monthPaymentTotal);
        result.put("activeOpportunityCount", activeOppCount);
        result.put("weightedOpportunityAmount", weightedOppAmount);
        result.put("leadTotal", leadTotal);
        result.put("sourceMix", sourceMix);
        result.put("customerTotal", customerTotal);
        result.put("customerHealthMix", healthCount);
        result.put("todoStats", Map.of("pending", todoPending, "completed", todoCompleted, "overdue", todoOverdue));
        result.put("reminders", reminders.stream().limit(10).toList());

        return result;
    }

    private List<Map<String, Object>> listRecords(String key, List<String> deptIds, String actor, String ownerName) {
        var rows = mapper.page(key, deptIds, actor, ownerName, null, null, false, null, Map.of(), null, "desc", 0, 5000);
        List<Map<String, Object>> list = new ArrayList<>();
        for (var row : rows) {
            list.add(toRecord(row));
        }
        return list;
    }

    private Map<String, Object> toRecord(Map<String, Object> row) {
        Map<String, Object> record = new LinkedHashMap<>();
        try {
            if (row.get("payload") != null) {
                record.putAll(json.readValue(String.valueOf(row.get("payload")), new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception ignored) {}
        record.put("id", row.get("id"));
        if (row.get("departmentId") != null) record.put("departmentId", row.get("departmentId"));
        if (row.get("ownerId") != null) record.put("ownerId", row.get("ownerId"));
        return record;
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
}
