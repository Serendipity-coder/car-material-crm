package com.checa.crm.resource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResourceRegistry {
    private ResourceRegistry() {}

    private static final Map<String, ResourceDefinition> BY_KEY = new LinkedHashMap<>();

    private static void put(String key, String path, String list, String add, String edit, String remove, String prefix) {
        BY_KEY.put(key, new ResourceDefinition(key, path, list, add, edit, remove, prefix, false, false));
    }

    private static void putReadOnly(String key, String path, String list) {
        BY_KEY.put(key, new ResourceDefinition(key, path, list, list, list, list, "rp_", true, false));
    }

    static {
        BY_KEY.put("todos", new ResourceDefinition("todos", "/todos", "work:todo:list", "work:todo:add", "work:todo:edit", "work:todo:edit", "t_", false, true));
        put("leads", "/leads", "crm:lead:list", "crm:lead:add", "crm:lead:edit", "crm:lead:remove", "l_");
        put("pool", "/pool", "crm:pool:list", "crm:pool:edit", "crm:pool:edit", "crm:pool:assign", "p_");
        put("customers", "/customers", "crm:customer:list", "crm:customer:add", "crm:customer:edit", "crm:customer:remove", "c_");
        put("stores", "/stores", "crm:customer:list", "crm:customer:edit", "crm:customer:edit", "crm:customer:remove", "st_store_");
        put("contacts", "/contacts", "crm:contact:list", "crm:contact:add", "crm:contact:edit", "crm:contact:remove", "co_");
        put("followups", "/followups", "crm:followup:list", "crm:followup:add", "crm:followup:edit", "crm:followup:remove", "f_");
        put("opportunities", "/opportunities", "crm:opportunity:list", "crm:opportunity:add", "crm:opportunity:edit", "crm:opportunity:remove", "o_");
        put("quotes", "/quotes", "sales:quote:list", "sales:quote:add", "sales:quote:edit", "sales:quote:remove", "q_");
        put("orders", "/orders", "sales:order:list", "sales:order:add", "sales:order:edit", "sales:order:remove", "so_");
        put("returns", "/returns", "sales:return:list", "sales:return:add", "sales:return:edit", "sales:return:remove", "rtn_");
        put("shipments", "/shipments", "delivery:shipment:list", "delivery:shipment:add", "delivery:shipment:edit", "delivery:shipment:edit", "sh_");
        put("payments", "/payments", "delivery:payment:list", "delivery:payment:add", "delivery:payment:edit", "delivery:payment:edit", "pay_");
        put("receivables", "/receivables", "delivery:receivable:list", "delivery:receivable:edit", "delivery:receivable:edit", "delivery:receivable:edit", "ar_");
        put("service-tickets", "/service-tickets", "service:ticket:list", "service:ticket:add", "service:ticket:edit", "service:ticket:edit", "st_");
        put("visits", "/visits", "service:visit:list", "service:visit:add", "service:visit:edit", "service:visit:edit", "v_");
        putReadOnly("reports-sales", "/reports/sales", "report:sales:list");
        putReadOnly("reports-customer-purchase", "/reports/customer-purchase", "report:customer:list");
        putReadOnly("reports-product-sales", "/reports/product-sales", "report:product:list");
    }

    public static ResourceDefinition byKey(String key) {
        return BY_KEY.get(key);
    }

    public static Collection<ResourceDefinition> all() {
        return BY_KEY.values();
    }

    /** 由请求路径解析资源，例如 /leads -> leads，/reports/sales -> reports-sales。 */
    public static ResourceDefinition byPath(String path) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        if (clean.startsWith("reports/")) return BY_KEY.get("reports-" + clean.substring("reports/".length()));
        return BY_KEY.get(clean);
    }
}
