package com.yuanqi.app.auth.controller;

import com.yuanqi.app.auth.dto.AuthRequests;
import com.yuanqi.app.auth.service.AuthService;
import com.yuanqi.app.auth.support.ClientInfo;
import com.yuanqi.app.auth.vo.LoginVO;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录、注册、刷新令牌、退出。
 * <p>路径前缀 /api/v1/auth（方案 A）。</p>
 */
@Tag(name = "1. 认证", description = "登录、注册与令牌管理")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录", description = "account 支持邮箱或用户名；失败 5 次锁定 15 分钟")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest httpRequest) {
        return Result.success(authService.login(request, ClientInfo.ip(httpRequest), ClientInfo.userAgent(httpRequest)));
    }

    @Operation(summary = "邮箱注册", description = "邮箱必填；注册成功后直接签发双令牌")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody AuthRequests.Register request, HttpServletRequest httpRequest) {
        return Result.success(authService.register(request, ClientInfo.ip(httpRequest), ClientInfo.userAgent(httpRequest)));
    }

    @Operation(summary = "刷新访问令牌", description = "refresh 旋转签发；旧 refresh 再次使用将吊销全部会话")
    @PostMapping("/token/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody AuthRequests.RefreshToken request, HttpServletRequest httpRequest) {
        return Result.success(authService.refresh(request, ClientInfo.ip(httpRequest), ClientInfo.userAgent(httpRequest)));
    }

    @Operation(
            summary = "退出登录",
            description = "吊销请求体中的 refresh 会话；需同时携带有效 access",
            security = @SecurityRequirement(name = "Authorization")
    )
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody AuthRequests.Logout request, HttpServletRequest httpRequest) {
        authService.logout(request, UserContext.getUserId(), ClientInfo.ip(httpRequest), ClientInfo.userAgent(httpRequest));
        return Result.success(null);
    }
}
