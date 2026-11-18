package com.checa.crm.service;

import com.checa.crm.exception.BusinessException;
import com.checa.crm.mapper.*;
import com.checa.crm.pojo.dto.OrgDtos;
import com.checa.crm.pojo.entity.OrgEntities;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.security.OrgPermissionGuard;
import com.checa.crm.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDate;

@Service
public class OrgUserService {
 private final OrgUserMapper users; private final OrgDeptMapper depts; private final OrgRoleMapper roles; private final OrgPermissionGuard guard;
 private final AuditLogService audit;
 public OrgUserService(OrgUserMapper users,OrgDeptMapper depts,OrgRoleMapper roles,OrgPermissionGuard guard,AuditLogService audit){this.users=users;this.depts=depts;this.roles=roles;this.guard=guard;this.audit=audit;}
 public OrgVos.Page<OrgVos.User> page(String actor,OrgDtos.UserQuery q){guard.require(actor,"system:user:list");int page=Math.max(1,q.pageNum()==null?1:q.pageNum()),size=Math.min(100,Math.max(1,q.pageSize()==null?10:q.pageSize()));var rows=users.list(q.keyword(),q.deptId(),queryStatus(q.status()));if(q.roleId()!=null&&!q.roleId().isBlank())rows=rows.stream().filter(x->users.roleIds(x.userId).contains(q.roleId())).toList();long total=rows.size();int from=Math.min((page-1)*size,rows.size());return new OrgVos.Page<>(rows.subList(from,Math.min(from+size,rows.size())).stream().map(this::toUser).toList(),total);}
 public List<OrgVos.User> all(String actor){guard.require(actor,"system:user:list");return users.list(null,null,null).stream().map(this::toUser).toList();}
 public OrgVos.User detail(String actor,String id){guard.require(actor,"system:user:list");var u=must(id);return toUser(u);}
 public OrgVos.UserSummary summary(String actor){guard.require(actor,"system:user:list");var all=users.list(null,null,null);return new OrgVos.UserSummary(all.size(),all.stream().filter(x->"0".equals(x.status)).count(),depts.list().stream().filter(x->"0".equals(x.status)).count(),all.stream().filter(x->x.hireDate!=null&&x.hireDate.getYear()==LocalDate.now().getYear()&&x.hireDate.getMonthValue()==LocalDate.now().getMonthValue()).count());}
 @Transactional public OrgVos.User create(String actor,OrgDtos.UserCreate x){guard.require(actor,"system:user:add");if(x.account()==null||x.account().isBlank()||x.password()==null||x.password().length()<6)throw new BusinessException("账号和初始密码不合法");if(users.countAccount(x.account())>0)throw new BusinessException("登录账号已存在（含已删除员工占用的账号），请更换");validate(x.departmentId(),x.roleIds(),x.phone(),"");String id="u_"+UUID.randomUUID().toString().replace("-","");users.insert(id,x.departmentId(),x.account(),PasswordUtil.hash(x.password()),x.name(),gender(x.gender()),x.phone(),x.email(),x.position(),normStatus(x.status()),x.hireDate(),x.remark());bindRoles(id,x.roleIds());audit.record(actor,"system-user",id,"CREATE",null,"新增员工 "+x.name());return toUser(must(id));}
 @Transactional public OrgVos.User update(String actor,String id,OrgDtos.UserUpdate x){guard.require(actor,"system:user:edit");var old=must(id);if(x.account()!=null&&!x.account().equals(old.username))throw new BusinessException("编辑时不能修改登录账号");validate(x.departmentId(),x.roleIds(),x.phone(),id);if("1".equals(normStatus(x.status()))&&isLastAdmin(id))throw new BusinessException("不能停用最后一位启用状态的超级管理员");users.update(id,x.departmentId(),x.name(),gender(x.gender()),x.phone(),x.email(),x.position(),normStatus(x.status()),x.hireDate(),x.remark(),x.password()==null||x.password().isBlank()?null:PasswordUtil.hash(x.password()));if(x.roleIds()!=null){if(isAdmin(id)&&x.roleIds().stream().noneMatch(r->"admin".equals(roles.find(r).roleKey))&&isLastAdmin(id))throw new BusinessException("不能移除最后一位启用状态的超级管理员");bindRoles(id,x.roleIds());}audit.record(actor,"system-user",id,"UPDATE",null,"编辑员工 "+x.name());return toUser(must(id));}
 @Transactional public void status(String actor,List<String> ids,String status){guard.require(actor,"system:user:edit");if(ids==null||ids.isEmpty())return;String next=normStatus(status);if("1".equals(next)&&ids.stream().anyMatch(this::isLastAdmin))throw new BusinessException("不能停用最后一位启用状态的超级管理员");ids.forEach(id->users.updateStatus(id,next));audit.record(actor,"system-user",String.join(",",ids),"STATUS",null,"批量状态 -> "+status);}
 private void validate(String dept,List<String> roleIds,String phone,String id){if(dept==null||depts.find(dept)==null)throw new BusinessException("所属部门不存在");if(phone!=null&&!phone.isBlank()&&users.countPhone(phone,id)>0)throw new BusinessException("手机号已被使用");if(roleIds==null||roleIds.isEmpty())throw new BusinessException("员工至少绑定一个角色");for(String r:roleIds){var role=roles.find(r);if(role==null||!"0".equals(role.status))throw new BusinessException("角色不存在或已停用");}}
 private void bindRoles(String id,List<String> ids){users.clearRoles(id);ids.forEach(r->users.bindRole(id,r));}
 private OrgEntities.User must(String id){var u=users.find(id);if(u==null)throw new BusinessException("员工不存在");return u;}
 private boolean isAdmin(String id){return users.roleIds(id).stream().map(roles::find).filter(Objects::nonNull).anyMatch(r->"admin".equals(r.roleKey));}
 private boolean isLastAdmin(String id){return isAdmin(id)&&users.list(null,null,"0").stream().filter(x->isAdmin(x.userId)).count()<=1;}
 private OrgVos.User toUser(OrgEntities.User u){return new OrgVos.User(u.userId,u.realName,u.username,u.deptId,u.position,users.roleIds(u.userId),u.phone,u.email,"0".equals(u.status)?"在职":"停用",u.hireDate,u.gender,u.remark,u.lastLoginIp,u.lastLoginTime);}
 private static String queryStatus(String s){return s==null||s.isBlank()?null:normStatus(s);} private static String normStatus(String s){return "在职".equals(s)||"启用".equals(s)||"0".equals(s)||s==null?"0":"1";} private static String gender(String s){return "女".equals(s)||"1".equals(s)?"1":"0";}
}
