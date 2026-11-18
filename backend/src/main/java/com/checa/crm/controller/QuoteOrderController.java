package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.QuoteOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/quotes/{id}")
public class QuoteOrderController {
    private final QuoteOrderService service;

    public QuoteOrderController(QuoteOrderService service) {
        this.service = service;
    }

    @PostMapping("/approve")
    public ApiResponse<Map<String, Object>> approve(
            @RequestAttribute("userId") String actor,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        String note = body == null || body.get("note") == null ? null : String.valueOf(body.get("note"));
        return ApiResponse.ok(service.approveQuote(actor, id, note, request.getRemoteAddr()));
    }

    @PostMapping("/reject")
    public ApiResponse<Map<String, Object>> reject(
            @RequestAttribute("userId") String actor,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        String reason = body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ApiResponse.ok(service.rejectQuote(actor, id, reason, request.getRemoteAddr()));
    }

    @PostMapping("/convert-to-order")
    public ApiResponse<Map<String, Object>> convertToOrder(
            @RequestAttribute("userId") String actor,
            @PathVariable String id,
            HttpServletRequest request) {
        return ApiResponse.ok(service.convertToOrder(actor, id, request.getRemoteAddr()));
    }
}
