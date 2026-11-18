package com.checa.crm.security;

import com.checa.crm.mapper.OrgDeptMapper;
import com.checa.crm.mapper.OrgUserMapper;
import com.checa.crm.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OrgDataScopeService {
 private final SysUserMapper sysUsers; private final OrgUserMapper users; private final OrgDeptMapper depts;
 public OrgDataScopeService(SysUserMapper sysUsers,OrgUserMapper users,OrgDeptMapper depts){this.sysUsers=sysUsers;this.users=users;this.depts=depts;}
 public List<String> departments(String actor){
  var permissions=sysUsers.permissions(actor); if(permissions.contains("*")||permissions.contains("system:data:all")) return null;
  var user=users.find(actor); if(user==null||user.deptId==null||user.deptId.isBlank()||"0".equals(user.deptId)) throw new com.checa.crm.exception.BusinessException("当前账号未关联部门，无法读取业务数据");
  Set<String> allowed=new LinkedHashSet<>(); collect(user.deptId,allowed); return new ArrayList<>(allowed);
 }
 private void collect(String id,Set<String> out){if(!out.add(id))return;for(var d:depts.list())if(id.equals(d.parentId))collect(d.deptId,out);}
}
