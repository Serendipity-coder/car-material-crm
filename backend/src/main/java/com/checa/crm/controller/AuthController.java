package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.pojo.dto.AuthDtos.*;
import com.checa.crm.pojo.vo.AuthVos.*;
import com.checa.crm.service.AuthService;
import com.checa.crm.service.SmsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final AuthService auth; private final SmsService sms;
    public AuthController(AuthService auth,SmsService sms){this.auth=auth;this.sms=sms;}
    @PostMapping({"/api/sms/send-code","/auth/sms/send-code"}) public ApiResponse<CodeVO> loginCode(@Valid @RequestBody SendLoginCodeRequest req){return ApiResponse.ok(sms.sendLoginCode(req.account(),req.phone()));}
    @PostMapping("/auth/register/send-code") public ApiResponse<CodeVO> registerCode(@Valid @RequestBody SendRegisterCodeRequest req){return ApiResponse.ok(sms.sendRegisterCode(req.phone()));}
    @PostMapping({"/auth/login/code","/auth/login"}) public ApiResponse<SessionVO> login(@Valid @RequestBody LoginRequest req,HttpServletRequest request){return ApiResponse.ok(auth.login(req,request.getRemoteAddr()));}
    @PostMapping("/auth/register") public ApiResponse<SessionVO> register(@Valid @RequestBody RegisterRequest req){return ApiResponse.ok(auth.register(req));}
    @GetMapping("/auth/me") public ApiResponse<UserVO> me(HttpServletRequest request){return ApiResponse.ok(auth.me((String)request.getAttribute("userId")));}
    @PatchMapping("/auth/me") public ApiResponse<UserVO> updateMe(@RequestAttribute("userId")String userId,@Valid @RequestBody ProfileUpdate req){return ApiResponse.ok(auth.updateProfile(userId,req));}
    @PostMapping("/auth/password") public ApiResponse<Void> changePassword(@RequestAttribute("userId")String userId,@Valid @RequestBody PasswordChange req){auth.changePassword(userId,req);return ApiResponse.ok();}
    @PostMapping("/auth/logout") public ApiResponse<Void> logout(HttpServletRequest request){auth.logout((String)request.getAttribute("accessToken"));return ApiResponse.ok();}
}
