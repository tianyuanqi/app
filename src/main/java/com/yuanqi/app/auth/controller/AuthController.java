package com.yuanqi.app.auth.controller;

import com.yuanqi.app.auth.dto.AuthRequests;
import com.yuanqi.app.auth.security.AuthCookieService;
import com.yuanqi.app.auth.security.CsrfTokenService;
import com.yuanqi.app.auth.security.OriginGuard;
import com.yuanqi.app.auth.service.AuthService;
import com.yuanqi.app.auth.service.AuthSessionService;
import com.yuanqi.app.auth.service.VerificationService;
import com.yuanqi.app.auth.support.ClientInfo;
import com.yuanqi.app.auth.support.EmailNormalizer;
import com.yuanqi.app.auth.vo.AuthViews;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.idempotency.IdempotencyService;
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

/** 认证 HTTP 入口：处理来源校验、会话 Cookie 与响应装配，业务状态交由 Service 管理。 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final VerificationService verificationService;
    private final AuthSessionService sessionService;
    private final AuthCookieService cookies;
    private final CsrfTokenService csrfTokens;
    private final OriginGuard originGuard;
    private final IdempotencyService idempotency;
    private final EmailNormalizer emails;

    public AuthController(AuthService authService, VerificationService verificationService,
                          AuthSessionService sessionService, AuthCookieService cookies,
                          CsrfTokenService csrfTokens, OriginGuard originGuard, IdempotencyService idempotency,
                          EmailNormalizer emails) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.sessionService = sessionService;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
        this.originGuard = originGuard;
        this.idempotency = idempotency;
        this.emails = emails;
    }

    @Operation(summary = "发送注册邮箱验证码")
    @PostMapping("/verification-codes")
    public Result<AuthViews.VerificationFlowView> sendCode(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AuthRequests.SendCode request, HttpServletRequest http) {
        originGuard.requireTrusted(http);
        String ip = ClientInfo.ip(http);
        String emailKey = emails.normalize(request.email());
        return idempotency.execute("verification:" + IdempotencyService.sha256(emailKey + "\n" + ip),
                "POST", "/api/v1/auth/verification-codes", key,
                java.util.Map.of("email", emailKey), AuthViews.VerificationFlowView.class,
                java.time.Duration.ofMinutes(10),
                () -> ResponseEntity.ok(Result.success(verificationService.sendCode(request.email(), ip)))).getBody();
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

    /** 校验来源后执行密码登录；响应体提供 Access Token，刷新凭证通过 Cookie 写入。 */
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

    /** 通过当前 refresh Cookie 恢复会话并重发 CSRF Cookie，不延长会话期限。 */
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

    /** 先解析会话并校验 CSRF，再旋转凭证；已旋转凭证会在解析阶段被拒绝。 */
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

    /** 有效会话须通过 CSRF 校验；缺失或解析为 SESSION_INVALID 的凭证仅清除 Cookie 并幂等成功。 */
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
        // 双提交值相等还不够，签名、会话绑定和有效期也必须通过校验。
        String cookie = cookies.csrfCookie(request);
        if (header == null || cookie == null || !header.equals(cookie) || !csrfTokens.valid(header, sessionId)) {
            throw new BusinessException(ErrorCode.CSRF_INVALID);
        }
    }

}
