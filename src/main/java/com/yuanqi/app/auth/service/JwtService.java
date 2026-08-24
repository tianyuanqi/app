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
