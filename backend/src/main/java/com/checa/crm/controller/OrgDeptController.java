package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.OrgDtos;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.service.OrgDeptService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/depts")
public class OrgDeptController {
 private final OrgDeptService service; public OrgDeptController(OrgDeptService service){this.service=service;}
 @GetMapping("/tree") public ApiResponse<List<OrgVos.Dept>> tree(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.tree(actor));}
 @PostMapping public ApiResponse<OrgVos.Dept> create(@RequestAttribute("userId") String actor,@RequestBody OrgDtos.DeptSave body){return ApiResponse.ok(service.create(actor,body));}
 @PutMapping("/{id}") public ApiResponse<OrgVos.Dept> update(@RequestAttribute("userId") String actor,@PathVariable String id,@RequestBody OrgDtos.DeptSave body){return ApiResponse.ok(service.update(actor,id,body));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@RequestAttribute("userId") String actor,@PathVariable String id){service.delete(actor,id);return ApiResponse.ok();}
}
