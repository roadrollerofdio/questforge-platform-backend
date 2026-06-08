package com.questforge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 认证授权请求载荷
 */
public class AuthDto {

    @Data
    public static class LoginReq {
        @NotBlank(message = "账号标识不能为空")
        private String username;

        @NotBlank(message = "安全口令不能为空")
        private String password;
    }

    @Data
    public static class LoginResp {
        private String token;
        private Long userId;
        private String role;
        private String realName;
    }
}