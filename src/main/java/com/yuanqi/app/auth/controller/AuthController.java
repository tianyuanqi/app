package com.yuanqi.app.auth.controller;

import com.yuanqi.app.auth.dto.AuthRequests;
import com.yuanqi.app.auth.security.AuthCookieService;
import com.yuanqi.app.auth.security.CsrfTokenService;
import com.yuanqi.app.auth.security.OriginGuard;
import com.yuanqi.app.auth.service.AuthService;
import com.yuanqi.app.auth.service.AuthSessionService;
import com.yuanqi.app.auth.service.VerificationService;
import com.yuanqi.app.auth.support.ClientInfo;
import com.yuanqi.app.auth.vo.AuthViews;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final AuthService authService;
    private final VerificationService verificationService;
    private final AuthSessionService sessionService;
    private final AuthCookieService cookies;
    private final CsrfTokenService csrfTokens;
    private final OriginGuard originGuard;

    public AuthController(AuthService authService, VerificationService verificationService,
                          AuthSessionService sessionService, AuthCookieService cookies,
                          CsrfTokenService csrfTokens, OriginGuard originGuard) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.sessionService = sessionService;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
        this.originGuard = originGuard;
    }

    @Operation(summary = "发送注册邮箱验证码")
    @PostMapping("/verification-codes")
    public Result<AuthViews.VerificationFlowView> sendCode(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AuthRequests.SendCode request, HttpServletRequest http) {
        originGuard.requireTrusted(http);
        requireIdempotencyKey(key);
        return Result.success(verificationService.sendCode(request.email(), ClientInfo.ip(http)));
    }

    @Operation(summary = "注册并自动登录")
    @PostMapping("/register")
    public ResponseEntity<Result<AuthViews.SessionView>> register(
            @Valid @RequestBody AuthRequests.Register request, HttpServletRequest http,
            HttpServletResponse response) {
        originGuard.requireTrusted(http);
        AuthSessionService.IssuedSession issued = authService.register(request, ClientInfo.ip(http));
        cookies.set(response, issued.refreshCredential(), issued.csrfToken(), issued.expiresAt());
        return ResponseEntity.ok(Result.success(issued.view()));
    }

    @Operation(summary = "邮箱密码登录")
    @PostMapping("/login")
    public ResponseEntity<Result<AuthViews.SessionView>> login(
            @Valid @RequestBody AuthRequests.Login request, HttpServletRequest http,
            HttpServletResponse response) {
        originGuard.requireTrusted(http);
        AuthSessionService.IssuedSession issued = authService.login(request, ClientInfo.ip(http));
        cookies.set(response, issued.refreshCredential(), issued.csrfToken(), issued.expiresAt());
        return ResponseEntity.ok(Result.success(issued.view()));
    }

    @Operation(summary = "获取当前 Session 的 CSRF Token，不旋转 Refresh")
    @GetMapping("/csrf")
    public Result<AuthViews.CsrfView> csrf(HttpServletRequest http, HttpServletResponse response) {
        originGuard.requireTrusted(http);
        AuthSessionService.ResolvedSession resolved = sessionService.resolve(cookies.refresh(http));
        String token = csrfTokens.issue(resolved.session().getSessionId(), resolved.session().getAbsoluteExpiresAt());
        cookies.setCsrf(response, token, resolved.session().getAbsoluteExpiresAt());
        return Result.success(new AuthViews.CsrfView(AuthCookieService.CSRF_COOKIE,
                AuthCookieService.CSRF_HEADER,
                resolved.session().getAbsoluteExpiresAt().atOffset(java.time.ZoneOffset.UTC)));
    }

    @Operation(summary = "旋转 Refresh 并签发新 Access Token")
    @PostMapping("/token/refresh")
    public Result<AuthViews.SessionView> refresh(HttpServletRequest http, HttpServletResponse response,
                                                 @RequestHeader(value = AuthCookieService.CSRF_HEADER,
                                                         required = false) String csrfHeader) {
        originGuard.requireTrusted(http);
        String rawRefresh = cookies.refresh(http);
        AuthSessionService.ResolvedSession resolved = sessionService.resolve(rawRefresh);
        requireCsrf(http, csrfHeader, resolved.session().getSessionId());
        AuthSessionService.IssuedSession issued = sessionService.rotate(rawRefresh);
        cookies.set(response, issued.refreshCredential(), issued.csrfToken(), issued.expiresAt());
        return Result.success(issued.view());
    }

    @Operation(summary = "退出当前 Session；无 Session 时幂等成功")
    @PostMapping("/logout")
    public Result<AuthViews.LogoutResult> logout(HttpServletRequest http, HttpServletResponse response,
                                                 @RequestHeader(value = AuthCookieService.CSRF_HEADER,
                                                         required = false) String csrfHeader) {
        originGuard.requireTrusted(http);
        String rawRefresh = cookies.refresh(http);
        if (rawRefresh == null || rawRefresh.isBlank()) {
            cookies.clear(response);
            return Result.success(new AuthViews.LogoutResult(true, true));
        }
        AuthSessionService.ResolvedSession resolved;
        try {
            resolved = sessionService.resolve(rawRefresh);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.SESSION_INVALID) {
                throw e;
            }
            cookies.clear(response);
            return Result.success(new AuthViews.LogoutResult(true, true));
        }
        requireCsrf(http, csrfHeader, resolved.session().getSessionId());
        boolean already = sessionService.logout(rawRefresh);
        cookies.clear(response);
        return Result.success(new AuthViews.LogoutResult(true, already));
    }

    private void requireCsrf(HttpServletRequest request, String header, String sessionId) {
        String cookie = cookies.csrfCookie(request);
        if (header == null || cookie == null || !header.equals(cookie) || !csrfTokens.valid(header, sessionId)) {
            throw new BusinessException(ErrorCode.CSRF_INVALID);
        }
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
    }
}
