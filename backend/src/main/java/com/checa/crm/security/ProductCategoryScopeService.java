package com.checa.crm.security;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.OrgRoleMapper;
import com.checa.crm.mapper.ProductMapper;
import com.checa.crm.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 产品品类数据范围：按角色与品类的授权关系计算当前用户可见/可销售的分类集合。
 * <p>规则（方案 5.10.3）：</p>
 * <ul>
 *   <li>拥有 {@code *} 或 {@code system:data:all} 的账号可见全部品类；</li>
 *   <li>角色授权父级品类时默认连同下级品类一并授权（include_children=1，可关闭）；</li>
 *   <li>{@code category_id='0'} 表示该角色拥有全部品类；</li>
 *   <li>返回 {@code null} 表示"不限品类"，返回空集合表示"没有任何品类权限"。</li>
 * </ul>
 */
@Service
public class ProductCategoryScopeService {
    private static final String ALL = "0";

    private final SysUserMapper users;
    private final OrgRoleMapper roles;
    private final ProductMapper products;

    public ProductCategoryScopeService(SysUserMapper users, OrgRoleMapper roles, ProductMapper products) {
        this.users = users;
        this.roles = roles;
        this.products = products;
    }

    /** 是否不受品类限制（管理员 / 全部品类授权）。 */
    public boolean unrestricted(String actor) {
        if (actor == null || actor.isBlank()) return false;
        var permissions = users.permissions(actor);
        if (permissions.contains("*") || permissions.contains("system:data:all")) return true;
        var roleIds = users.roleIds(actor);
        if (roleIds.isEmpty()) return false;
        return bindings(roleIds).stream().anyMatch(b -> ALL.equals(b.categoryId));
    }

    /** 当前用户被授权的品类（含父级继承的下级）；null 表示不限。 */
    public List<String> authorized(String actor) {
        if (unrestricted(actor)) return null;
        Set<String> out = new LinkedHashSet<>();
        for (var binding : bindings(users.roleIds(actor))) {
            if (binding.categoryId == null || ALL.equals(binding.categoryId)) continue;
            if (binding.includeChildren == null || binding.includeChildren != 0) out.addAll(products.categorySubtree(binding.categoryId));
            else out.add(binding.categoryId);
        }
        return new ArrayList<>(out);
    }

    /** 分类树/分类列表展示用：授权品类 + 其祖先，保证树结构完整；null 表示不限。 */
    public List<String> visible(String actor) {
        var authorized = authorized(actor);
        if (authorized == null) return null;
        Set<String> out = new LinkedHashSet<>(authorized);
        for (String id : authorized) out.addAll(products.categoryAncestors(id));
        return new ArrayList<>(out);
    }

    /** 校验品类是否在当前用户范围内，不在则 403。 */
    public void require(String actor, String categoryId) {
        var authorized = authorized(actor);
        if (authorized == null) return;
        if (categoryId == null || !authorized.contains(categoryId)) throw new BusinessException(403, "无权访问该产品品类");
    }

    public boolean allows(String actor, String categoryId) {
        var authorized = authorized(actor);
        return authorized == null || (categoryId != null && authorized.contains(categoryId));
    }

    private List<com.checa.crm.pojo.entity.OrgEntities.RoleCategory> bindings(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return List.of();
        return roles.categoryBindings(roleIds);
    }
}
