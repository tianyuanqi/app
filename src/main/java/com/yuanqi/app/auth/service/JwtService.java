package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** 签发和解析 Access Token；服务端会话、账号归属与当前角色由认证过滤器复核。 */
@Service
public class JwtService {
    private final JwtProperties properties;
    private final AuthProperties authProperties;
    private final Clock clock;

    public JwtService(JwtProperties properties, AuthProperties authProperties, Clock clock) {
        this.properties = properties;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    /** 使用公开 uid 和 sid 签发令牌；有效期取配置毫秒时长与 UTC 会话剩余期限的较短者。 */
    public AccessToken createAccessToken(Account account, String sessionId, LocalDateTime sessionExpiresAt) {
        Instant issuedAt = clock.instant();
        Instant configuredExpiry = issuedAt.plusMillis(authProperties.getAccessExpireMs());
        Instant sessionExpiry = sessionExpiresAt.toInstant(ZoneOffset.UTC);
        Instant expiresAt = configuredExpiry.isBefore(sessionExpiry) ? configuredExpiry : sessionExpiry;
        String token = Jwts.builder()
                .subject(account.getUid())
                .claims(Map.of("uid", account.getUid(), "role", account.getRole(), "sid", sessionId))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
        return new AccessToken(token, expiresAt);
    }

    /**
     * 解析签名令牌；过期令牌保留声明并标记 expired，其他解析异常返回 null。
     * 过期结果仅供过滤器决定错误优先级，不代表认证成功。
     */
    public ParsedAccess parseAccess(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
            return new ParsedAccess(claims.get("uid", String.class), claims.get("role", String.class),
                    claims.get("sid", String.class), false);
        } catch (ExpiredJwtException e) {
            Claims claims = e.getClaims();
            return new ParsedAccess(claims.get("uid", String.class), claims.get("role", String.class),
                    claims.get("sid", String.class), true);
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey signingKey() {
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要 32 字节");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    public record AccessToken(String value, Instant expiresAt) {
    }

    public record ParsedAccess(String uid, String role, String sessionId, boolean expired) {
    }
}
