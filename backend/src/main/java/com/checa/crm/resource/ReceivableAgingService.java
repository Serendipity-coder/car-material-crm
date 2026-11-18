package com.checa.crm.resource;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 应收账款账龄与逾期动态计算服务（方案 6.11）：
 * 账龄天数（agingDays）根据应收截止日（dueDate）与当前系统日期动态计算，
 * 并结合已收金额（received）与应收金额（amount）重算结算与逾期状态。
 */
@Service
public class ReceivableAgingService {

    public void recalculate(Map<String, Object> payload) {
        if (payload == null) return;
        BigDecimal amount = decimal(payload.get("amount"));
        BigDecimal received = decimal(payload.get("received"));
        if (amount == null) amount = BigDecimal.ZERO;
        if (received == null) received = BigDecimal.ZERO;

        BigDecimal outstanding = amount.subtract(received);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;
        payload.put("outstanding", outstanding);

        // 已收齐全额 -> 已结清
        if (amount.compareTo(BigDecimal.ZERO) > 0 && received.compareTo(amount) >= 0) {
            payload.put("status", "已结清");
            payload.put("agingDays", 0);
            payload.put("isOverdue", false);
            return;
        }

        String dueStr = text(payload.get("dueDate"));
        LocalDate dueDate = parseDate(dueStr);
        LocalDate today = LocalDate.now();

        if (dueDate != null) {
            if (today.isAfter(dueDate)) {
                long days = ChronoUnit.DAYS.between(dueDate, today);
                payload.put("agingDays", (int) days);
                payload.put("status", "已逾期");
                payload.put("isOverdue", true);
            } else {
                payload.put("agingDays", 0);
                payload.put("status", "待回款");
                payload.put("isOverdue", false);
            }
        } else {
            // 没有明确到期日，保留原有或默认 0
            if (payload.get("agingDays") == null) payload.put("agingDays", 0);
            if (payload.get("status") == null) payload.put("status", "待回款");
            payload.put("isOverdue", false);
        }
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
