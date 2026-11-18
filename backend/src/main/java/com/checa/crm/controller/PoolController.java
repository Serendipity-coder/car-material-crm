package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.resource.PoolAutoRecycleService;
import com.checa.crm.resource.PoolService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公海动作：线索公海与客户公海的领取、分配、回收与历史。
 * 普通增删改仍走通用资源接口（/pool、/leads）。
 */
@RestController
public class PoolController {
    private final PoolService service;
    private final PoolAutoRecycleService autoRecycle;

    public PoolController(PoolService service, PoolAutoRecycleService autoRecycle) {
        this.service = service;
        this.autoRecycle = autoRecycle;
    }

    @PostMapping("/pool/{id}/claim")
    public ApiResponse<Map<String, Object>> claimPool(@RequestAttribute("userId") String actor, @PathVariable String id) {
        return ApiResponse.ok(service.claimPool(actor, id));
    }

    @PostMapping("/pool/{id}/assign")
    public ApiResponse<Map<String, Object>> assignPool(@RequestAttribute("userId") String actor, @PathVariable String id,
                                                       @RequestBody Map<String, Object> body) {
        Object owner = body == null ? null : body.get("owner");
        return ApiResponse.ok(service.assignPool(actor, id, owner == null ? null : String.valueOf(owner)));
    }

    /** 将客户档案回收到客户公海。 */
    @PostMapping("/pool/recycle")
    public ApiResponse<Map<String, Object>> recycle(@RequestAttribute("userId") String actor, @RequestBody Map<String, Object> body) {
        Object id = body == null ? null : (body.get("customerId") != null ? body.get("customerId") : body.get("id"));
        return ApiResponse.ok(service.recycleCustomer(actor, id == null ? null : String.valueOf(id)));
    }

    @GetMapping("/pool/{id}/history")
    public ApiResponse<List<Map<String, Object>>> poolHistory(@RequestAttribute("userId") String actor, @PathVariable String id) {
        return ApiResponse.ok(service.history(actor, id, "pool"));
    }

    @PostMapping("/leads/{id}/claim")
    public ApiResponse<Map<String, Object>> claimLead(@RequestAttribute("userId") String actor, @PathVariable String id) {
        return ApiResponse.ok(service.claimLead(actor, id));
    }

    @PostMapping("/leads/{id}/recycle")
    public ApiResponse<Map<String, Object>> recycleLead(@RequestAttribute("userId") String actor, @PathVariable String id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        Object note = body == null ? null : body.get("note");
        return ApiResponse.ok(service.releaseLead(actor, id, note == null ? null : String.valueOf(note)));
    }

    /** 手动触发长期未跟进回收；days 不传时使用系统参数。 */
    @PostMapping("/pool/auto-recycle")
    public ApiResponse<Map<String, Object>> autoRecycle(@RequestAttribute("userId") String actor,
                                                        @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(autoRecycle.manual(actor, days));
    }

    @GetMapping("/leads/{id}/history")
    public ApiResponse<List<Map<String, Object>>> leadHistory(@RequestAttribute("userId") String actor, @PathVariable String id) {
        return ApiResponse.ok(service.history(actor, id, "leads"));
    }
}
