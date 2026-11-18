package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.service.OrgRoleService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/menus")
public class SystemMenuController {
 private final OrgRoleService service;
 public SystemMenuController(OrgRoleService service){this.service=service;}
 @GetMapping("/tree") public ApiResponse<List<OrgVos.Menu>> tree(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.menuTree(actor));}
}
