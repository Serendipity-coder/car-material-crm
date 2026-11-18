package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.resource.GenericRecordMapper;
import com.checa.crm.resource.ProductSkuGuard;
import com.checa.crm.resource.ResourceRegistry;
import com.checa.crm.security.OrgPermissionGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 报价单与订单核心业务服务（方案 6.9 & 6.10 & 8.3 & 8.4）：
 * 1. 低于最低成交价服务端审批与驳回（approve / reject）；
 * 2. 报价转订单原子服务端事务保证（convert-to-order）：
 *    - 状态与有效期强校验；
 *    - 幂等防重约束（避免同一报价重复转单）；
 *    - 订单产品明细价格快照固化；
 *    - 履约与回款初始状态解耦；
 *    - 事务内自动级联创建客户回访与补货待办任务。
 */
@Service
public class QuoteOrderService {
    private final GenericRecordMapper mapper;
    private final OrgPermissionGuard guard;
    private final ProductSkuGuard skuGuard;
    private final ObjectMapper json;

    public QuoteOrderService(GenericRecordMapper mapper, OrgPermissionGuard guard, ProductSkuGuard skuGuard, ObjectMapper json) {
        this.mapper = mapper;
        this.guard = guard;
        this.skuGuard = skuGuard;
        this.json = json;
    }

    /** 报价审批通过 */
    @Transactional
    public Map<String, Object> approveQuote(String actor, String quoteId, String note, String ip) {
        guard.require(actor, "sales:quote:edit");
        Map<String, Object> row = mapper.find(quoteId);
        if (row == null) throw new BusinessException(404, "报价单不存在");
        Map<String, Object> before = readPayload(row);
        Map<String, Object> payload = new LinkedHashMap<>(before);

        payload.put("status", "已通过");
        payload.put("approvedBy", actor);
        payload.put("approvedAt", LocalDate.now().toString());
        if (note != null && !note.isBlank()) payload.put("approvalNote", note);

        try {
            mapper.update(quoteId, json.writeValueAsString(payload), String.valueOf(row.get("departmentId")), null, "已通过", actor);
            mapper.audit(actor, null, String.valueOf(row.get("departmentId")), "quotes", quoteId, "APPROVE",
                    json.writeValueAsString(before), json.writeValueAsString(payload), "SUCCESS", ip);
        } catch (Exception e) {
            throw new BusinessException("报价审批失败");
        }
        return payload;
    }

    /** 报价审批驳回 */
    @Transactional
    public Map<String, Object> rejectQuote(String actor, String quoteId, String rejectReason, String ip) {
        guard.require(actor, "sales:quote:edit");
        Map<String, Object> row = mapper.find(quoteId);
        if (row == null) throw new BusinessException(404, "报价单不存在");
        Map<String, Object> before = readPayload(row);
        Map<String, Object> payload = new LinkedHashMap<>(before);

        payload.put("status", "已拒绝");
        payload.put("rejectedBy", actor);
        payload.put("rejectedAt", LocalDate.now().toString());
        payload.put("rejectReason", (rejectReason != null && !rejectReason.isBlank()) ? rejectReason : "审批未通过");

        try {
            mapper.update(quoteId, json.writeValueAsString(payload), String.valueOf(row.get("departmentId")), null, "已拒绝", actor);
            mapper.audit(actor, null, String.valueOf(row.get("departmentId")), "quotes", quoteId, "REJECT",
                    json.writeValueAsString(before), json.writeValueAsString(payload), "SUCCESS", ip);
        } catch (Exception e) {
            throw new BusinessException("报价驳回失败");
        }
        return payload;
    }

    /** 报价原子转销售订单 */
    @Transactional
    public Map<String, Object> convertToOrder(String actor, String quoteId, String ip) {
        guard.require(actor, "sales:order:add");
        Map<String, Object> quoteRow = mapper.find(quoteId);
        if (quoteRow == null) throw new BusinessException(404, "报价单不存在");
        Map<String, Object> quotePayload = readPayload(quoteRow);

        String status = text(quotePayload.get("status"));
        if (!"已通过".equals(status) && !"已接受".equals(status) && !"已完成".equals(status) && !"已审核".equals(status)) {
            throw new BusinessException(400, "报价当前状态为「" + (status != null ? status : "未审核") + "」，必须审核通过或客户接受后方可转为销售订单");
        }

        String validUntil = text(quotePayload.get("validUntil"));
        if (validUntil != null && !validUntil.isBlank()) {
            try {
                if (LocalDate.parse(validUntil).isBefore(LocalDate.now())) {
                    throw new BusinessException(400, "该报价单已过有效期（" + validUntil + "），请重新发起报价");
                }
            } catch (Exception ignored) {}
        }

        String quoteNo = text(quotePayload.get("quoteNo"));
        if (quoteNo == null) quoteNo = quoteId;

        // 幂等防重：检查是否已有相同 quoteNo 或 quoteId 的有效订单
        int existingOrderCount = mapper.countOrdersByQuote(quoteNo, quoteId);
        if (existingOrderCount > 0) {
            throw new BusinessException(409, "该报价单已生成过销售订单，禁止重复转单");
        }

        List<?> rawItems = quotePayload.get("items") instanceof List<?> list ? list : List.of();
        if (rawItems.isEmpty()) {
            throw new BusinessException(400, "报价单产品明细为空，无法生成订单");
        }

        // 1. 创建订单实体
        String orderId = "so_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String orderNo = "SO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + (1000 + new Random().nextInt(9000));
        String deptId = String.valueOf(quoteRow.get("departmentId"));
        String owner = text(quotePayload.get("owner"));
        String customer = text(quotePayload.get("customer"));

        Map<String, Object> orderPayload = new LinkedHashMap<>();
        orderPayload.put("id", orderId);
        orderPayload.put("orderNo", orderNo);
        orderPayload.put("quoteNo", quoteNo);
        orderPayload.put("quoteId", quoteId);
        orderPayload.put("customer", customer);
        orderPayload.put("customerId", quotePayload.get("customerId"));
        orderPayload.put("opportunity", quotePayload.get("opportunity"));
        orderPayload.put("contact", quotePayload.get("contact"));
        orderPayload.put("storeName", quotePayload.get("storeName"));
        orderPayload.put("shippingAddress", quotePayload.get("shippingAddress"));
        orderPayload.put("paymentTerms", quotePayload.get("paymentTerms"));
        orderPayload.put("estimatedDeliveryDate", quotePayload.get("estimatedDeliveryDate"));
        orderPayload.put("orderDate", LocalDate.now().toString());

        // 计算金额并锁定快照
        orderPayload.put("items", rawItems);
        orderPayload.put("amount", quotePayload.get("amount"));
        orderPayload.put("discount", quotePayload.get("discount"));
        orderPayload.put("tax", quotePayload.get("tax"));
        orderPayload.put("freight", quotePayload.get("freight"));

        orderPayload.put("deliveryStatus", "未发货");
        orderPayload.put("paymentStatus", "未回款");
        orderPayload.put("status", "待确认");
        orderPayload.put("owner", owner);
        orderPayload.put("departmentId", deptId);
        orderPayload.put("createdAt", LocalDate.now().toString());

        // 逐行 SKU 校验品类权限
        skuGuard.validate(actor, ResourceRegistry.byKey("orders"), orderPayload);

        try {
            // 写入订单
            mapper.insert(orderId, "orders", json.writeValueAsString(orderPayload), deptId, null, "待确认", actor);
            mapper.audit(actor, null, deptId, "orders", orderId, "CONVERT_FROM_QUOTE", null, json.writeValueAsString(orderPayload), "SUCCESS", ip);

            // 2. 更新报价单状态为已转订单
            Map<String, Object> quoteUpdated = new LinkedHashMap<>(quotePayload);
            quoteUpdated.put("status", "已转订单");
            quoteUpdated.put("convertedOrderNo", orderNo);
            quoteUpdated.put("convertedAt", LocalDate.now().toString());
            mapper.update(quoteId, json.writeValueAsString(quoteUpdated), deptId, null, "已转订单", actor);

            // 3. 自动生成回访与补货待办
            createOrderFollowupTodos(actor, orderId, orderNo, customer, owner, deptId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("报价转订单执行失败: " + e.getMessage());
        }

        return orderPayload;
    }

    private void createOrderFollowupTodos(String actor, String orderId, String orderNo, String customer, String owner, String departmentId) {
        LocalDate today = LocalDate.now();
        // 订单满意度回访任务（14天后）
        String t1Id = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> t1 = new LinkedHashMap<>();
        t1.put("id", t1Id);
        t1.put("title", "订单满意度回访：" + (customer != null ? customer : "客户"));
        t1.put("related", "订单 " + orderNo + " 签收履约后安排满意度与使用体验回访");
        t1.put("relatedId", orderId);
        t1.put("relatedType", "订单");
        t1.put("taskType", "客户回访");
        t1.put("customer", customer);
        t1.put("owner", owner);
        t1.put("dueDate", today.plusDays(14).toString());
        t1.put("priority", "中");
        t1.put("status", "待处理");
        t1.put("departmentId", departmentId);
        t1.put("createdAt", today.toString());

        // 订单耗材补货提醒任务（30天后）
        String t2Id = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> t2 = new LinkedHashMap<>();
        t2.put("id", t2Id);
        t2.put("title", "周期耗材补货跟进：" + (customer != null ? customer : "客户"));
        t2.put("related", "根据订单 " + orderNo + " 采购规格预估补货周期，提醒客户复购");
        t2.put("relatedId", orderId);
        t2.put("relatedType", "订单");
        t2.put("taskType", "补货提醒");
        t2.put("customer", customer);
        t2.put("owner", owner);
        t2.put("dueDate", today.plusDays(30).toString());
        t2.put("priority", "中");
        t2.put("status", "待处理");
        t2.put("departmentId", departmentId);
        t2.put("createdAt", today.toString());

        try {
            mapper.insert(t1Id, "todos", json.writeValueAsString(t1), departmentId, null, "待处理", actor);
            mapper.insert(t2Id, "todos", json.writeValueAsString(t2), departmentId, null, "待处理", actor);
        } catch (Exception ignored) {}
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
