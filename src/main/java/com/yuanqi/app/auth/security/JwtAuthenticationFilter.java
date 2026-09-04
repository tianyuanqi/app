package com.yuanqi.app.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.entity.AuthSession;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.service.AuthSessionService;
import com.yuanqi.app.auth.service.JwtService;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult;
import com.yuanqi.app.common.context.UserContext;
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
import java.util.List;

/** Access Token 携带公开 uid、sid 和角色快照；认证时复核服务端会话、账号归属及当前角色。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthSessionService sessionService;
    private final AccountMapper accountMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, AuthSessionService sessionService,
                                   AccountMapper accountMapper, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.accountMapper = accountMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null) {
                if (!header.startsWith("Bearer ") || header.length() == 7) {
                    writeError(response, ErrorCode.SESSION_INVALID);
                    return;
                }
                JwtService.ParsedAccess parsed = jwtService.parseAccess(header.substring(7));
                if (parsed == null) {
                    writeError(response, ErrorCode.SESSION_INVALID);
                    return;
                }
                // 先报告会话失效或账号不可用，再判断 Access Token 过期，避免错误地提示客户端刷新。
                AuthSession session = sessionService.findActiveSession(parsed.sessionId());
                if (session == null) {
                    writeError(response, ErrorCode.SESSION_INVALID);
                    return;
                }
                Account account = accountMapper.findByUid(parsed.uid());
                if (account == null || !account.getId().equals(session.getAccountId())) {
                    writeError(response, ErrorCode.SESSION_INVALID);
                    return;
                }
                if ("DISABLED".equals(account.getGovernanceStatus())) {
                    writeError(response, ErrorCode.ACCOUNT_UNAVAILABLE);
                    return;
                }
                if (parsed.expired()) {
                    writeError(response, ErrorCode.ACCESS_TOKEN_EXPIRED);
                    return;
                }
                String role = account.getRole() == null ? "USER" : account.getRole();
                UserContext.setUserId(account.getId());
                UserContext.setUid(account.getUid());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        account.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            }
            chain.doFilter(request, response);
        } finally {
            // 无论下游成功还是抛异常，都清理请求线程上的身份，避免线程复用时残留。
            UserContext.remove();
            SecurityContextHolder.clearContext();
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResult.of(code, code.getMessage()));
    }
}
