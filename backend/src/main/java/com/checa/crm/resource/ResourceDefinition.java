package com.checa.crm.resource;

/**
 * 通用业务资源定义：前端 21 个业务模块中有 18 个走同一套 CRUD 契约，
 * 这里集中声明它们的路径、权限码、编号前缀与数据范围语义，避免为每个模块重复写 Controller。
 */
public record ResourceDefinition(
        String key,
        String path,
        String listPermission,
        String addPermission,
        String editPermission,
        String removePermission,
        String idPrefix,
        boolean readOnly,
        boolean ownerScoped
) {
    public boolean exists() {
        return key != null;
    }
}
