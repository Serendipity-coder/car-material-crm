package com.checa.crm.resource;

import com.checa.crm.security.DataScopeService;
import com.checa.crm.security.OrgPermissionGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报表服务：在服务端完成日期区间过滤与聚合（按月份 / 客户 / 产品），
 * 返回结构与前端报表列保持一致，前端不再承担聚合职责。
 */
@Service
public class ReportService {
    private static final int MAX_ROWS = 20000;
    private static final Pattern MONTH = Pattern.compile("(\\d{4})\\D+(\\d{1,2})");

    private final GenericRecordMapper mapper;
    private final DataScopeService dataScope;
    private final ObjectMapper json;
    private final OrgPermissionGuard guard;

    public ReportService(GenericRecordMapper mapper, DataScopeService dataScope, ObjectMapper json, OrgPermissionGuard guard) {
        this.mapper = mapper; this.dataScope = dataScope; this.json = json; this.guard = guard;
    }

    public GenericResourceService.PageResult<Map<String, Object>> page(String actor, ResourceDefinition def, Map<String, String> params) {
        var scope = dataScope.resolve(actor, "reports");
        String requested = blank(params.get("departmentId"));
        List<String> deptIds;
        String selfId = null, selfName = null;
        if (scope.all()) deptIds = requested == null ? null : List.of(requested);
        else if (scope.selfOnly()) { deptIds = null; selfId = actor; selfName = scope.userName(); }
        else deptIds = scope.deptIds();

        int page = Math.max(1, parseInt(params.get("pageNum"), 1));
        int size = Math.min(1000, Math.max(1, parseInt(params.get("pageSize"), 20)));
        List<Map<String, Object>> rows = mapper.page(def.key(), deptIds, actor, null, selfId, selfName, false,
                blank(params.get("keyword")), Map.of(), null, "desc", 0, MAX_ROWS).stream().map(this::toRecord).toList();

        rows = inRange(def.key(), rows, params.get("startDate"), params.get("endDate"));
        Map<String, Integer> opportunities = "reports-sales".equals(def.key())
                ? opportunitiesByMonth(deptIds, actor, selfId, selfName) : Map.of();
        List<Map<String, Object>> aggregated = aggregate(def.key(), rows, opportunities);
        // 聚合后可能补入「仅有商机、无签约」的月份，需要再按区间裁剪一次
        aggregated = inRange(def.key(), aggregated, params.get("startDate"), params.get("endDate"));
        // 毛利率属敏感价格字段：无 crm:price:sensitive 权限则打码
        if (!guard.has(actor, "system:data:all") && !guard.has(actor, "crm:price:sensitive")) {
            for (var row : aggregated) row.put("margin", null);
        }
        int from = Math.min((page - 1) * size, aggregated.size());
        return new GenericResourceService.PageResult<>(aggregated.subList(from, Math.min(from + size, aggregated.size())), aggregated.size());
    }

    // ---------------- 日期区间 ----------------
    private List<Map<String, Object>> inRange(String key, List<Map<String, Object>> rows, String startText, String endText) {
        LocalDate start = parseDate(startText), end = parseDate(endText);
        if (start == null && end == null) return rows;
        List<Map<String, Object>> out = new ArrayList<>();
        for (var row : rows) {
            YearMonth month = monthOf(dateValue(key, row));
            if (month == null) continue;
            LocalDate first = month.atDay(1), last = month.atEndOfMonth();
            if (start != null && last.isBefore(start)) continue;
            if (end != null && first.isAfter(end)) continue;
            out.add(row);
        }
        return out;
    }

    private static String dateValue(String key, Map<String, Object> row) {
        return switch (key) {
            case "reports-sales" -> text(row.get("month"));
            case "reports-customer-purchase" -> text(row.get("lastPurchase"));
            case "reports-product-sales" -> text(row.get("month"));
            default -> null;
        };
    }

    private static YearMonth monthOf(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = MONTH.matcher(value);
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            return month >= 1 && month <= 12 ? YearMonth.of(year, month) : null;
        }
        try { return YearMonth.parse(value.length() >= 7 ? value.substring(0, 7) : value); } catch (Exception e) { return null; }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value); } catch (Exception e) { return null; }
    }

    // ---------------- 聚合 ----------------
    private List<Map<String, Object>> aggregate(String key, List<Map<String, Object>> rows, Map<String, Integer> opportunities) {
        return switch (key) {
            case "reports-sales" -> aggregateSales(rows, opportunities);
            case "reports-customer-purchase" -> aggregateCustomer(rows);
            case "reports-product-sales" -> aggregateProduct(rows);
            default -> rows;
        };
    }

    /** 按创建月份统计新增商机数量（用于销售统计的「新增商机」卡片）。 */
    private Map<String, Integer> opportunitiesByMonth(List<String> deptIds, String actor, String selfId, String selfName) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = mapper.page("opportunities", deptIds, actor, null, selfId, selfName, false,
                null, Map.of(), null, "desc", 0, MAX_ROWS);
        for (var row : rows) {
            YearMonth month = monthOf(date(row.get("createdAt")));
            if (month == null) continue;
            out.merge(label(month), 1, Integer::sum);
        }
        return out;
    }

    private static String label(YearMonth month) {
        return month.getYear() + "年" + month.getMonthValue() + "月";
    }

    private List<Map<String, Object>> aggregateSales(List<Map<String, Object>> rows, Map<String, Integer> opportunities) {
        Map<String, List<Map<String, Object>>> grouped = group(rows, "month");
        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            double amount = 0, target = 0; int orders = 0, newCustomers = 0, conversionSum = 0, conversionCount = 0;
            for (var row : entry.getValue()) {
                amount += number(row.get("amount"));
                target += number(row.get("targetAmount"));
                orders += (int) number(row.get("orders"));
                newCustomers += (int) number(row.get("newCustomers"));
                if (row.get("conversion") != null) { conversionSum += (int) number(row.get("conversion")); conversionCount++; }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "agg-sales-" + entry.getKey());
            item.put("month", entry.getKey());
            item.put("amount", round(amount));
            item.put("orders", orders);
            item.put("newCustomers", newCustomers);
            item.put("conversion", conversionCount == 0 ? 0 : Math.round((float) conversionSum / conversionCount));
            item.put("targetAmount", round(target));
            item.put("opportunities", opportunities.getOrDefault(entry.getKey(), 0));
            out.add(item);
        }
        // 该月没有签约数据但产生了商机时，补一行，保证「新增商机」合计不丢数
        for (var entry : opportunities.entrySet()) {
            if (grouped.containsKey(entry.getKey())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "agg-sales-" + entry.getKey());
            item.put("month", entry.getKey());
            item.put("amount", 0);
            item.put("orders", 0);
            item.put("newCustomers", 0);
            item.put("conversion", 0);
            item.put("targetAmount", 0);
            item.put("opportunities", entry.getValue());
            out.add(item);
        }
        out.sort(Comparator.comparing((Map<String, Object> row) -> monthOf(text(row.get("month"))), Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private List<Map<String, Object>> aggregateCustomer(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = group(rows, "customer");
        Map<String, String> levels = new HashMap<>();
        for (var row : mapper.customerLevels()) {
            String name = text(row.get("name")), level = text(row.get("level"));
            if (name != null && level != null) levels.put(name, level);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            double amount = 0; int orders = 0, growthSum = 0, growthCount = 0; String lastPurchase = null, industry = null;
            for (var row : entry.getValue()) {
                amount += number(row.get("amount"));
                orders += (int) number(row.get("orders"));
                if (row.get("growth") != null) { growthSum += (int) number(row.get("growth")); growthCount++; }
                String purchase = text(row.get("lastPurchase"));
                if (purchase != null && (lastPurchase == null || purchase.compareTo(lastPurchase) > 0)) lastPurchase = purchase;
                if (industry == null) industry = text(row.get("industry"));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "agg-customer-" + entry.getKey());
            item.put("customer", entry.getKey());
            item.put("industry", industry == null ? "—" : industry);
            item.put("amount", round(amount));
            item.put("orders", orders);
            item.put("growth", growthCount == 0 ? 0 : Math.round((float) growthSum / growthCount));
            item.put("lastPurchase", lastPurchase);
            item.put("level", levels.getOrDefault(entry.getKey(), "—"));
            out.add(item);
        }
        out.sort(Comparator.comparingDouble((Map<String, Object> row) -> number(row.get("amount"))).reversed());
        return out;
    }

    private List<Map<String, Object>> aggregateProduct(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = group(rows, "product");
        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            double amount = 0, quantity = 0, marginWeighted = 0; int growthSum = 0, growthCount = 0; String category = null;
            for (var row : entry.getValue()) {
                double rowAmount = number(row.get("amount"));
                amount += rowAmount;
                quantity += number(row.get("quantity"));
                marginWeighted += number(row.get("margin")) * rowAmount;
                if (row.get("growth") != null) { growthSum += (int) number(row.get("growth")); growthCount++; }
                if (category == null) category = text(row.get("category"));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "agg-product-" + entry.getKey());
            item.put("product", entry.getKey());
            item.put("category", category == null ? "—" : category);
            item.put("quantity", round(quantity));
            item.put("amount", round(amount));
            item.put("margin", amount == 0 ? 0 : Math.round(marginWeighted / amount));
            item.put("growth", growthCount == 0 ? 0 : Math.round((float) growthSum / growthCount));
            out.add(item);
        }
        out.sort(Comparator.comparingDouble((Map<String, Object> row) -> number(row.get("amount"))).reversed());
        return out;
    }

    private static Map<String, List<Map<String, Object>>> group(List<Map<String, Object>> rows, String key) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            String value = text(row.get(key));
            if (value == null) continue;
            grouped.computeIfAbsent(value, k -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    // ---------------- 工具 ----------------
    private Map<String, Object> toRecord(Map<String, Object> row) {
        Map<String, Object> payload = read(String.valueOf(row.get("payload")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        payload.forEach((k, v) -> { if (!"id".equals(k)) out.put(k, v); });
        if (out.get("departmentId") == null && row.get("departmentId") != null) out.put("departmentId", row.get("departmentId"));
        if (out.get("status") == null && row.get("metaStatus") != null) out.put("status", row.get("metaStatus"));
        if (out.get("createdAt") == null) out.put("createdAt", date(row.get("createdAt")));
        out.put("updatedAt", date(row.get("updatedAt")));
        return out;
    }

    private Map<String, Object> read(String payload) {
        try {
            return json.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String date(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }

    private static double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
    }

    private static double round(double value) { return Math.round(value * 100d) / 100d; }

    private static int parseInt(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String text(Object value) {
        if (value == null) return null;
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }
}
