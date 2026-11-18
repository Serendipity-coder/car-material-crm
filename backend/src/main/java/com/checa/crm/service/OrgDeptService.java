package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.*;
import com.checa.crm.pojo.dto.OrgDtos;
import com.checa.crm.pojo.entity.OrgEntities;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.security.OrgPermissionGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class OrgDeptService {
 private final OrgDeptMapper depts; private final OrgUserMapper users; private final OrgPermissionGuard guard;
 private final AuditLogService audit;
 public OrgDeptService(OrgDeptMapper depts,OrgUserMapper users,OrgPermissionGuard guard,AuditLogService audit){this.depts=depts;this.users=users;this.guard=guard;this.audit=audit;}
 public List<OrgVos.Dept> tree(String actor){guard.require(actor,"system:dept:list");return build(depts.list(),"0");}
 @Transactional public OrgVos.Dept create(String actor,OrgDtos.DeptSave x){guard.require(actor,"system:dept:add");String parent=parent(x.parentId());if(!"0".equals(parent)&&depts.find(parent)==null)throw new BusinessException("上级部门不存在");if(depts.countSame("",x.name(),parent)>0)throw new BusinessException("同级部门名称已存在");String id="d_"+UUID.randomUUID().toString().replace("-","");depts.insert(id,parent,ancestors(parent),x.name(),blank(x.code()),x.sort()==null?0:x.sort(),blank(x.leaderId()),blank(x.phone()),blank(x.email()),normStatus(x.status()));audit.record(actor,"system-dept",id,"CREATE",null,"新增部门 "+x.name());return to(depts.find(id),new HashMap<>());}
 @Transactional public OrgVos.Dept update(String actor,String id,OrgDtos.DeptSave x){guard.require(actor,"system:dept:edit");if(depts.find(id)==null)throw new BusinessException("部门不存在");String parent=parent(x.parentId());if(!"0".equals(parent)&&depts.find(parent)==null)throw new BusinessException("上级部门不存在");if(id.equals(parent)||descendants(id).contains(parent))throw new BusinessException("上级部门不能选择自身或其下级部门");if(depts.countSame(id,x.name(),parent)>0)throw new BusinessException("同级部门名称已存在");depts.update(id,parent,ancestors(parent),x.name(),blank(x.code()),x.sort()==null?0:x.sort(),blank(x.leaderId()),blank(x.phone()),blank(x.email()),normStatus(x.status()));recomputeAncestors(id);audit.record(actor,"system-dept",id,"UPDATE",null,"编辑部门 "+x.name());return to(depts.find(id),new HashMap<>());}
 @Transactional public void delete(String actor,String id){guard.require(actor,"system:dept:remove");if(depts.find(id)==null)throw new BusinessException("部门不存在");if(depts.countChildren(id)>0)throw new BusinessException("该部门存在下级部门，请先迁移或删除下级部门");if(depts.countUsers(id)>0)throw new BusinessException("该部门仍有直属员工，请先迁移员工");depts.delete(id);audit.record(actor,"system-dept",id,"DELETE","删除部门 "+id,null);}
 private List<String> descendants(String id){List<String> out=new ArrayList<>();for(var d:depts.list())if(id.equals(d.parentId)){out.add(d.deptId);out.addAll(descendants(d.deptId));}return out;}
 private void recomputeAncestors(String parentId){var parent=depts.find(parentId);if(parent==null)return;String base=parent.ancestors+","+parent.deptId;for(var d:depts.list())if(parentId.equals(d.parentId)){depts.updateAncestors(d.deptId,base);recomputeAncestors(d.deptId);}}
 private List<OrgVos.Dept> build(List<OrgEntities.Dept> all,String parent){return all.stream().filter(d->parent.equals(parent(d.parentId))).map(d->new OrgVos.Dept(d.deptId,"0".equals(d.parentId)?null:d.parentId,d.deptName,d.deptCode,d.leaderId,d.orderNum,d.phone,d.email,"0".equals(d.status)?"启用":"停用",build(all,d.deptId),users.countDept(d.deptId))).toList();}
 private OrgVos.Dept to(OrgEntities.Dept d,Map<String,List<OrgVos.Dept>> ignored){return new OrgVos.Dept(d.deptId,"0".equals(d.parentId)?null:d.parentId,d.deptName,d.deptCode,d.leaderId,d.orderNum,d.phone,d.email,"0".equals(d.status)?"启用":"停用",List.of(),users.countDept(d.deptId));}
 private static String parent(String p){return p==null||p.isBlank()?"0":p;} private String ancestors(String p){if("0".equals(p))return "0";var d=depts.find(p);return d==null?"0":("0".equals(d.ancestors)?"0,"+p:d.ancestors+","+p);} private static String blank(String s){return s==null?"":s;} private static String normStatus(String s){return "启用".equals(s)||"0".equals(s)||s==null?"0":"1";}
}
