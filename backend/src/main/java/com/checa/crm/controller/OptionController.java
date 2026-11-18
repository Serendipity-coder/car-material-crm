package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.mapper.OrgUserMapper;
import com.checa.crm.pojo.entity.OrgEntities;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 下拉选项接口。前端不再内置客户/产品/负责人等写死列表，统一从这里按当前库里的人员返回。
 */
@RestController
@RequestMapping("/options")
public class OptionController {
    private final OrgUserMapper users;

    public OptionController(OrgUserMapper users) {
        this.users = users;
    }

    /** 可选负责人姓名（仅启用账号，只返回姓名，不暴露手机号/邮箱等敏感信息）。 */
    @GetMapping("/owners")
    public ApiResponse<List<String>> owners() {
        List<String> names = users.list(null, null, "0").stream()
                .map((OrgEntities.User u) -> u.realName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        return ApiResponse.ok(names);
    }
}
