package com.yuanqi.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录/注册/刷新成功后返回的令牌与基础身份信息。
 * <p>对外身份以 uid 为准；userId 为内部主键，暂留兼容，后续版本可能移除。</p>
 */
@Data
@Schema(description = "登录成功返回的身份信息")
public class LoginVO {

    @Schema(description = "兼容字段：与 accessToken 相同，建议改用 accessToken")
    private String token;

    @Schema(description = "JWT 访问令牌")
    private String accessToken;

    @Schema(description = "JWT 刷新令牌")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "accessToken 有效秒数", example = "1800")
    private Long expiresIn;

    @Schema(description = "对外业务 UID（推荐客户端持久化此字段）")
    private String uid;

    @Schema(description = "内部用户 ID（暂留兼容，不建议新客户端依赖）")
    private Long userId;

    @Schema(description = "用户名", example = "user1")
    private String username;

    @Schema(description = "用户角色", example = "USER")
    private String role;

    @Schema(description = "账号状态", example = "ACTIVE")
    private String accountStatus;
}
