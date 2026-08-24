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

/** Access Token 仅携带公开 uid；账号、角色和 Session 状态始终从服务端复核。 */
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
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        account.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            }
            chain.doFilter(request, response);
        } finally {
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
