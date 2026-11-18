package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.OrgDtos;
import com.checa.crm.pojo.vo.OrgVos;
import com.checa.crm.service.OrgUserService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/users")
public class OrgUserController {
 private final OrgUserService service; public OrgUserController(OrgUserService service){this.service=service;}
 @GetMapping public ApiResponse<OrgVos.Page<OrgVos.User>> page(@RequestAttribute("userId") String actor,@RequestParam(defaultValue="1") Integer pageNum,@RequestParam(defaultValue="10") Integer pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String deptId,@RequestParam(required=false) String roleId,@RequestParam(required=false) String status){return ApiResponse.ok(service.page(actor,new OrgDtos.UserQuery(pageNum,pageSize,keyword,deptId,roleId,status)));}
 @GetMapping("/summary") public ApiResponse<OrgVos.UserSummary> summary(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.summary(actor));}
 @GetMapping("/{id}") public ApiResponse<OrgVos.User> detail(@RequestAttribute("userId") String actor,@PathVariable String id){return ApiResponse.ok(service.detail(actor,id));}
 @GetMapping("/all") public ApiResponse<List<OrgVos.User>> all(@RequestAttribute("userId") String actor){return ApiResponse.ok(service.all(actor));}
 @PostMapping public ApiResponse<OrgVos.User> create(@RequestAttribute("userId") String actor,@RequestBody OrgDtos.UserCreate body){return ApiResponse.ok(service.create(actor,body));}
 @PutMapping("/{id}") public ApiResponse<OrgVos.User> update(@RequestAttribute("userId") String actor,@PathVariable String id,@RequestBody OrgDtos.UserUpdate body){return ApiResponse.ok(service.update(actor,id,body));}
 @PatchMapping("/status") public ApiResponse<Void> status(@RequestAttribute("userId") String actor,@RequestBody OrgDtos.StatusBatch body){service.status(actor,body.ids(),body.status());return ApiResponse.ok();}
}
