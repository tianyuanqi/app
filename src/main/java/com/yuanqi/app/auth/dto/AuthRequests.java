package com.yuanqi.app.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证相关请求 DTO。
 * <p>复杂规则（用户名字符集、密码复杂度）在服务层 {@code AuthPolicy} 二次校验。</p>
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    @Data
    @Schema(description = "登录请求：account 支持邮箱或用户名")
    public static class Login {
        @Schema(description = "登录账号（邮箱或用户名）", example = "user1@example.com")
        @NotBlank(message = "登录账号不能为空")
        private String account;

        @Schema(description = "密码", example = "Passw0rd")
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    @Schema(description = "注册请求（当前仅支持邮箱注册）")
    public static class Register {
        @Schema(description = "用户名", example = "photographer01")
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须为3到32个字符")
        private String username;

        @Schema(description = "密码", example = "Passw0rd")
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为8到72个字符")
        private String password;

        @Schema(description = "邮箱（必填）", example = "user@example.com")
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    @Schema(description = "刷新令牌请求")
    public static class RefreshToken {
        @NotBlank(message = "刷新令牌不能为空")
        private String refreshToken;
    }

    @Data
    @Schema(description = "退出登录请求")
    public static class Logout {
        @Schema(description = "需要吊销的刷新令牌")
        @NotBlank(message = "刷新令牌不能为空")
        private String refreshToken;
    }
}
