package com.yuanqi.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录成功返回的身份信息")
public class LoginVO {

    // 核心：用于后续接口鉴权的通行证
    @Schema(description = "JWT 访问令牌，后续请求需放在 Header 的 Authorization 中", example = "eyJhbGciOiJIUzI1...")
    private String token;

    // 基础信息：供前端快速渲染页面使用
    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户名", example = "Allen")
    private String username;

    @Schema(description = "用户角色", example = "ADMIN")
    private String role; // 比如 "ADMIN" 或 "USER"，前端可以据此控制菜单按钮的显示与隐藏
}