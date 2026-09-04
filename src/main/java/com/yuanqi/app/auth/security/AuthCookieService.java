package com.yuanqi.app.auth.security;

import com.yuanqi.app.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/** 装配认证 Cookie：refresh 为 HttpOnly，CSRF 可由浏览器脚本读取并回传请求头。 */
@Service
public class AuthCookieService {
    public static final String REFRESH_COOKIE = "__Host-2400px_refresh";
    public static final String CSRF_COOKIE = "__Host-2400px_csrf";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final AuthProperties properties;
    private final Clock clock;

    public AuthCookieService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 按 UTC 会话绝对期限计算 Cookie 剩余秒数；重新写入 Cookie 不延长服务端会话。 */
    public void set(HttpServletResponse response, String refresh, String csrf, LocalDateTime expiresAt) {
        long seconds = Math.max(0, expiresAt.toEpochSecond(ZoneOffset.UTC) - clock.instant().getEpochSecond());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, refresh, true, seconds).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, csrf, false, seconds).toString());
    }

    public void setCsrf(HttpServletResponse response, String csrf, LocalDateTime expiresAt) {
        long seconds = Math.max(0, expiresAt.toEpochSecond(ZoneOffset.UTC) - clock.instant().getEpochSecond());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, csrf, false, seconds).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", true, 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(CSRF_COOKIE, "", false, 0).toString());
    }

    public String refresh(HttpServletRequest request) {
        return value(request, REFRESH_COOKIE);
    }

    public String csrfCookie(HttpServletRequest request) {
        return value(request, CSRF_COOKIE);
    }

    private String value(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : Arrays.stream(cookies).filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private ResponseCookie cookie(String name, String value, boolean httpOnly, long seconds) {
        // __Host- 前缀要求 Secure、Path=/ 且不设置 Domain；Secure 仍取决于配置，关闭时浏览器可能拒收。
        return ResponseCookie.from(name, value).httpOnly(httpOnly).secure(properties.isSecureCookies())
                .sameSite("Lax").path("/").maxAge(Duration.ofSeconds(seconds)).build();
    }
}
