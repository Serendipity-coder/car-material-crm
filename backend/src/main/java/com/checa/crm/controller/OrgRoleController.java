package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.OrgDtos;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.service.OrgRoleService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/roles")
public class OrgRoleController {
 private final OrgRoleService service; public OrgRoleController(OrgRoleService service){this.service=service;}
 @GetMapping public ApiResponse<List<OrgVos.Role>> list(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.list(actor));}
 @GetMapping("/{id}") public ApiResponse<OrgVos.Role> detail(@RequestAttribute("userId") String actor,@PathVariable String id){return ApiResponse.ok(service.detail(actor,id));}
 @PostMapping public ApiResponse<OrgVos.Role> create(@RequestAttribute("userId") String actor,@RequestBody OrgDtos.RoleSave body){return ApiResponse.ok(service.create(actor,body));}
 @PutMapping("/{id}") public ApiResponse<OrgVos.Role> update(@RequestAttribute("userId") String actor,@PathVariable String id,@RequestBody OrgDtos.RoleSave body){return ApiResponse.ok(service.update(actor,id,body));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@RequestAttribute("userId") String actor,@PathVariable String id){service.delete(actor,id);return ApiResponse.ok();}
 @PutMapping("/{id}/menus") public ApiResponse<OrgVos.Role> menus(@RequestAttribute("userId") String actor,@PathVariable String id,@RequestBody OrgDtos.RoleMenus body){service.menus(actor,id,body.menuIds());return ApiResponse.ok(service.detail(actor,id));}
 @GetMapping("/menus/tree") public ApiResponse<List<OrgVos.Menu>> menuTree(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.menuTree(actor));}
 // ---- 产品品类授权 ----
 @GetMapping("/{id}/categories") public ApiResponse<OrgVos.RoleCategories> categories(@RequestAttribute("userId") String actor,@PathVariable String id){return ApiResponse.ok(service.categories(actor,id));}
 @PutMapping("/{id}/categories") public ApiResponse<OrgVos.RoleCategories> saveCategories(@RequestAttribute("userId") String actor,@PathVariable String id,@RequestBody OrgDtos.RoleCategorySave body){return ApiResponse.ok(service.saveCategories(actor,id,body));}
 @GetMapping("/categories/tree") public ApiResponse<List<OrgVos.CategoryNode>> categoryTree(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.categoryTree(actor));}
 // ---- 数据范围 ----
 @GetMapping("/{id}/data-scopes") public ApiResponse<OrgVos.RoleDataScopes> dataScopes(@RequestAttribute("userId") String actor,@PathVariable String id){return ApiResponse.ok(service.dataScopes(actor,id));}
 @PutMapping("/{id}/data-scopes") public ApiResponse<OrgVos.RoleDataScopes> saveDataScopes(@RequestAttribute("userId") String actor,@PathVariable String id,@RequestBody OrgDtos.RoleDataScopeSave body){return ApiResponse.ok(service.saveDataScopes(actor,id,body));}
}
