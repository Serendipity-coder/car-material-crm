package com.checa.crm.resource;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通用资源入口：/leads、/pool、/customers、/contacts、/followups、/opportunities、/quotes、/orders、
 * /returns、/shipments、/payments、/receivables、/service-tickets、/visits、/todos，
 * 以及只读报表 /reports/sales、/reports/customer-purchase、/reports/product-sales。
 * 产品分类、产品、客户价格仍由各自专用 Controller 处理（字面路径优先匹配）。
 */
@RestController
public class GenericResourceController {
    private final GenericResourceService service;

    public GenericResourceController(GenericResourceService service) {
        this.service = service;
    }

    @GetMapping({"/{resource}", "/{resource}/{child}"})
    public ApiResponse<Object> list(@RequestAttribute("userId") String actor, @PathVariable String resource,
                                    @PathVariable(required = false) String child,
                                    @RequestParam Map<String, String> params, HttpServletRequest request) {
        ResourceDefinition def = resolve(resource, child);
        if (child != null && !"reports".equals(resource)) return ApiResponse.ok(service.get(actor, def, child));
        return ApiResponse.ok(service.list(actor, def, params, request.getRemoteAddr()));
    }

    @PostMapping("/{resource}")
    public ApiResponse<Object> create(@RequestAttribute("userId") String actor, @PathVariable String resource,
                                      @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ApiResponse.ok(service.create(actor, resolve(resource, null), body, request.getRemoteAddr()));
    }

    @PutMapping("/{resource}/{id}")
    public ApiResponse<Object> update(@RequestAttribute("userId") String actor, @PathVariable String resource,
                                      @PathVariable String id, @RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        return ApiResponse.ok(service.update(actor, resolve(resource, null), id, body, request.getRemoteAddr()));
    }

    @DeleteMapping({"/{resource}", "/{resource}/{id}"})
    public ApiResponse<Void> delete(@RequestAttribute("userId") String actor, @PathVariable String resource,
                                    @PathVariable(required = false) String id,
                                    @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        ResourceDefinition def = resolve(resource, id);
        List<String> ids = id != null ? List.of(id) : idsOf(body);
        service.delete(actor, def, ids, request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PatchMapping("/{resource}/status")
    public ApiResponse<Void> status(@RequestAttribute("userId") String actor, @PathVariable String resource,
                                    @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        List<String> ids = idsOf(body);
        String status = body == null || body.get("status") == null ? null : String.valueOf(body.get("status"));
        service.status(actor, resolve(resource, null), ids, status, request.getRemoteAddr());
        return ApiResponse.ok();
    }

    private ResourceDefinition resolve(String resource, String child) {
        String key = "reports".equals(resource) ? "reports-" + (child == null ? "" : child) : resource;
        ResourceDefinition def = ResourceRegistry.byKey(key);
        if (def == null) throw new BusinessException(404, "接口不存在");
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<String> idsOf(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> list)) return List.of();
        return ((List<Object>) list).stream().filter(Objects -> Objects != null).map(String::valueOf).toList();
    }
}
