package com.questforge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 存放登录认证相关的入参/出参 DTO
 */
public class AuthDto {

    /**
     * 登录请求体
     */
    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    /**
     * 注册请求体
     */
    @Data
    public static class RegisterReq {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        @NotBlank(message = "真实姓名不能为空")
        private String realName;

        private String roleCode = "USER"; // 默认为考生
    }

    /**
     * 登录成功返回体
     */
    @Data
    public static class LoginResp {
        private String token;
        private String userId;
        private String role;
        private String realName;
    }
}