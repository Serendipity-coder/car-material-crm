package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.OrgRoleMapper;
import com.checa.crm.pojo.dto.OrgDtos;
import com.checa.crm.pojo.entity.OrgEntities;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.security.OrgPermissionGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class OrgRoleService {
 private final OrgRoleMapper roles; private final OrgPermissionGuard guard; private final AuditLogService audit;
 public OrgRoleService(OrgRoleMapper roles,OrgPermissionGuard guard,AuditLogService audit){this.roles=roles;this.guard=guard;this.audit=audit;}
 public List<OrgVos.Role> list(String actor){guard.require(actor,"system:role:list");return roles.list().stream().map(this::to).toList();}
 public OrgVos.Role detail(String actor,String id){guard.require(actor,"system:role:list");return to(must(id));}
 public List<OrgVos.Menu> menuTree(String actor){guard.require(actor,"system:role:list");var all=roles.menus();return build(all,"0");}
 @Transactional public OrgVos.Role create(String actor,OrgDtos.RoleSave x){guard.require(actor,"system:role:add");if(roles.countKey("",x.key())>0)throw new BusinessException("角色标识已存在");String id="r_"+UUID.randomUUID().toString().replace("-","");roles.insert(id,x.name(),x.key(),x.sort()==null?0:x.sort(),norm(x.status()),x.remark());audit.record(actor,"system-role",id,"CREATE",null,"新增角色 "+x.name());return to(must(id));}
 @Transactional public OrgVos.Role update(String actor,String id,OrgDtos.RoleSave x){guard.require(actor,"system:role:edit");var old=must(id);if("admin".equals(old.roleKey))throw new BusinessException("内置超级管理员不能修改关键标识或停用");if(roles.countKey(id,x.key())>0)throw new BusinessException("角色标识已存在");roles.update(id,x.name(),x.key(),x.sort()==null?0:x.sort(),norm(x.status()),x.remark());audit.record(actor,"system-role",id,"UPDATE",null,"编辑角色 "+x.name());return to(must(id));}
 @Transactional public void status(String actor,String id,String status){guard.require(actor,"system:role:edit");var r=must(id);if("admin".equals(r.roleKey))throw new BusinessException("内置超级管理员不能停用");roles.update(id,r.roleName,r.roleKey,r.sort,norm(status),r.remark);audit.record(actor,"system-role",id,"STATUS",null,"状态 -> "+status);}
 @Transactional public void delete(String actor,String id){guard.require(actor,"system:role:remove");var r=must(id);if("admin".equals(r.roleKey))throw new BusinessException("内置超级管理员不能删除");if(roles.memberCount(id)>0)throw new BusinessException("已分配给员工的角色不能删除");roles.delete(id);audit.record(actor,"system-role",id,"DELETE","删除角色 "+r.roleName,null);}
 @Transactional public void menus(String actor,String id,List<String> ids){
  guard.require(actor,"system:role:assign");
  var r=must(id);
  var all=roles.menus();
  List<String> target=ids;
  if("admin".equals(r.roleKey)){if(target==null||target.isEmpty())throw new BusinessException("超级管理员必须保留全部权限");target=all.stream().map(x->x.menuId).toList();}
  if(target!=null&&!all.stream().map(x->x.menuId).toList().containsAll(target))throw new BusinessException("权限节点不存在或已停用");
  final List<String> chosen=target;
  if(chosen!=null&&!"admin".equals(r.roleKey)&&all.stream().anyMatch(x->chosen.contains(x.menuId)&&"*".equals(x.perms)))throw new BusinessException("不能授予“全部权限(*)”节点，请选择具体权限");
  roles.clearMenus(id);
  if(chosen!=null)for(String menu:chosen)roles.bindMenu(id,menu);
  audit.record(actor,"system-role",id,"ASSIGN_MENU",null,"分配权限 "+(chosen==null?0:chosen.size())+" 项");
 }
 private OrgVos.Role to(OrgEntities.Role r){List<String> ids=roles.menuIds(r.roleId);return new OrgVos.Role(r.roleId,r.roleName,r.roleKey,r.sort,"0".equals(r.status)?"启用":"停用",roles.memberCount(r.roleId),ids,r.remark,"admin".equals(r.roleKey));}
 // ---------------- 角色产品品类授权 ----------------
 /** 查看角色的品类授权；all=true 表示该角色拥有全部品类。 */
 public OrgVos.RoleCategories categories(String actor,String id){
  guard.require(actor,"system:role:assign");
  var role=must(id);
  if("admin".equals(role.roleKey))return new OrgVos.RoleCategories(true,List.of());
  var bindings=roles.categoryBindings(List.of(id));
  boolean all=bindings.stream().anyMatch(b->"0".equals(b.categoryId));
  var items=bindings.stream().filter(b->!"0".equals(b.categoryId))
    .map(b->new OrgVos.RoleCategoryBinding(b.categoryId,b.includeChildren==null||b.includeChildren!=0)).toList();
  return new OrgVos.RoleCategories(all,items);
 }
 /** 覆盖式保存角色品类授权：all=true 授权全部品类，否则按 categoryIds 授权（可控制是否连同下级）。 */
 @Transactional public OrgVos.RoleCategories saveCategories(String actor,String id,OrgDtos.RoleCategorySave x){
  guard.require(actor,"system:role:assign");
  var role=must(id);
  boolean all=Boolean.TRUE.equals(x.all())||"admin".equals(role.roleKey);
  List<String> ids=x.categoryIds()==null?List.of():x.categoryIds().stream().filter(v->v!=null&&!v.isBlank()).distinct().toList();
  if(!all){
   var valid=new HashSet<String>(); roles.categoryNodes().forEach(n->valid.add(n.categoryId));
   for(String c:ids)if(!valid.contains(c))throw new BusinessException("产品品类不存在：" + c);
  }
  roles.clearCategories(id);
  if(all)roles.bindCategory(id,"0",1);
  else{
   int includeChildren=x.includeChildren()==null||x.includeChildren()?1:0;
   for(String c:ids)roles.bindCategory(id,c,includeChildren);
  }
  audit.record(actor,"system-role",id,"ASSIGN_CATEGORY",null,all?"授权全部品类":"授权品类 "+ids.size()+" 项");
  return categories(actor,id);
 }
 /** 品类树（全量，供角色授权界面勾选）。 */
 public List<OrgVos.CategoryNode> categoryTree(String actor){
  guard.require(actor,"system:role:assign");
  return buildCategoryTree(roles.categoryNodes(),"0");
 }
 // ---------------- 角色数据范围 ----------------
 private static final List<String> DATA_SCOPE_MODULES=List.of("leads","customers","opportunities","quotes","orders","reports");
 private static final java.util.Set<String> DATA_SCOPE_TYPES=java.util.Set.of("SELF","DEPT","DEPT_AND_CHILD","ALL","CUSTOM");
 public OrgVos.RoleDataScopes dataScopes(String actor,String id){
  guard.require(actor,"system:role:assign");
  var role=must(id);
  if("admin".equals(role.roleKey))return new OrgVos.RoleDataScopes(allScopeItems());
  var items=roles.dataScopes(List.of(id)).stream().map(r->new OrgVos.RoleDataScopeItem(r.moduleKey,r.scopeType,r.scopeValue)).toList();
  return new OrgVos.RoleDataScopes(items);
 }
 @Transactional public OrgVos.RoleDataScopes saveDataScopes(String actor,String id,OrgDtos.RoleDataScopeSave x){
  guard.require(actor,"system:role:assign");
  var role=must(id);
  if("admin".equals(role.roleKey))throw new BusinessException("超级管理员固定为企业全部数据范围");
  var items=x==null||x.items()==null?List.<OrgDtos.DataScopeItem>of():x.items();
  var seen=new HashSet<String>();
  for(var item:items){
   if(item==null||item.moduleKey()==null||!DATA_SCOPE_MODULES.contains(item.moduleKey()))throw new BusinessException("数据范围模块不存在");
   if(item.scopeType()==null||!DATA_SCOPE_TYPES.contains(item.scopeType()))throw new BusinessException("数据范围档位不正确");
   if(!seen.add(item.moduleKey()))throw new BusinessException("同一模块只能配置一个数据范围");
   if("CUSTOM".equals(item.scopeType())&&(item.scopeValue()==null||item.scopeValue().isBlank()))throw new BusinessException("指定部门范围需要选择部门");
  }
  roles.clearDataScopes(id);
  for(var item:items)roles.bindDataScope(id,item.moduleKey(),item.scopeType(),item.scopeValue());
  audit.record(actor,"system-role",id,"ASSIGN_SCOPE",null,"数据范围 "+items.size()+" 项");
  return dataScopes(actor,id);
 }
 private List<OrgVos.RoleDataScopeItem> allScopeItems(){
  return DATA_SCOPE_MODULES.stream().map(m->new OrgVos.RoleDataScopeItem(m,"ALL",null)).toList();
 }
 private List<OrgVos.CategoryNode> buildCategoryTree(List<OrgEntities.CategoryNode> all,String parent){
  return all.stream().filter(x->parent.equals(x.parentId==null||x.parentId.isBlank()?"0":x.parentId))
    .map(x->new OrgVos.CategoryNode(x.categoryId,x.parentId,x.categoryName,x.sortNum,x.status,buildCategoryTree(all,x.categoryId))).toList();
 }
 private OrgEntities.Role must(String id){var r=roles.find(id);if(r==null)throw new BusinessException("角色不存在");return r;}
 private List<OrgVos.Menu> build(List<OrgEntities.Menu> all,String p){return all.stream().filter(x->(p.equals(x.parentId)||("0".equals(p)&&(x.parentId==null||x.parentId.isBlank())))).map(x->new OrgVos.Menu(x.menuId,x.parentId,x.menuName,x.menuType,x.perms,x.sort,build(all,x.menuId))).toList();}
 private static String norm(String s){return "启用".equals(s)||"0".equals(s)||s==null?"0":"1";}
}
