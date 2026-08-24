package com.yuanqi.app.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanqi.app.auth.service.JwtService;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.user.entity.User;
import com.yuanqi.app.user.enums.AccountStatus;
import com.yuanqi.app.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JWT 认证过滤器。
 * <p>
 * 1. 解析 Bearer access Token；<br>
 * 2. 写入 UserContext 与 SecurityContext（含真实角色）；<br>
 * 3. 二次校验账号状态，禁用/锁定用户即使 Token 未过期也拒绝。
 * </p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Claims claims = jwtService.parseAccessClaims(token);
                if (claims != null) {
                    Long userId = jwtService.readUserId(claims);
                    User user = userId == null ? null : userMapper.selectById(userId);
                    if (user == null) {
                        writeError(response, ErrorCode.SESSION_INVALID);
                        return;
                    }
                    // 账号状态二次校验：禁用或仍在锁定期内则拒绝访问
                    if (AccountStatus.DISABLED.name().equals(user.getAccountStatus())) {
                        writeError(response, ErrorCode.ACCOUNT_UNAVAILABLE);
                        return;
                    }
                    if (AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                            && user.getLockedUntil() != null
                            && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                        writeError(response, ErrorCode.ACCOUNT_LOCKED);
                        return;
                    }

                    String role = user.getRole() == null ? "USER" : user.getRole();
                    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    UserContext.setUserId(userId);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    List.of(new SimpleGrantedAuthority(authority)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (requiresAuthenticationHint(request)) {
                    writeError(response, ErrorCode.SESSION_INVALID);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            writeError(response, ex.getErrorCode(), ex.getMessage());
        } finally {
            UserContext.remove();
            SecurityContextHolder.clearContext();
        }
    }

    /** 对明显需要登录的路径，非法 Token 直接 401，避免落到 403 */
    private boolean requiresAuthenticationHint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/users/me")
                || uri.startsWith("/api/v1/auth/logout")
                || uri.startsWith("/api/v1/moderation/")
                || uri.contains("/mine")
                || uri.contains("/my-list")
                || uri.endsWith("/submit")
                || ("POST".equalsIgnoreCase(request.getMethod()) && uri.startsWith("/api/v1/photos"))
                || ("PUT".equalsIgnoreCase(request.getMethod()) && uri.startsWith("/api/v1/photos/"))
                || ("DELETE".equalsIgnoreCase(request.getMethod()) && uri.startsWith("/api/v1/photos/"));
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        writeError(response, errorCode, errorCode.getMessage());
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResult.of(errorCode, message));
    }
}
