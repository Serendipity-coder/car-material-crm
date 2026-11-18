package com.checa.crm.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank String account,@NotBlank String password,@NotBlank @Pattern(regexp="^\\d{7,15}$") String phone,@NotBlank @Pattern(regexp="^\\d{4}$") String verificationCode) {}
    public record SendLoginCodeRequest(@NotBlank String account,@NotBlank @Pattern(regexp="^\\d{7,15}$") String phone) {}
    public record SendRegisterCodeRequest(@NotBlank @Pattern(regexp="^\\d{7,15}$") String phone) {}
    public record RegisterRequest(@NotBlank @Pattern(regexp="^[a-zA-Z][a-zA-Z0-9_]{2,63}$") String account,@Size(max=100) String realName,@NotBlank @Pattern(regexp="^\\d{7,15}$") String phone,@NotBlank @Pattern(regexp="^\\d{4}$") String verificationCode,@NotBlank @Size(min=6,max=64) String password,@NotBlank @Size(min=6,max=64) String confirmPassword,@Size(max=100) String email,@Size(max=100) String position,@Size(max=64) String deptId) {}
    public record ProfileUpdate(@Size(max=100) String name,@Size(max=30) String phone,@Size(max=100) String email) {}
    public record PasswordChange(@NotBlank String oldPassword,@NotBlank @Size(min=6,max=64) String newPassword) {}
}
