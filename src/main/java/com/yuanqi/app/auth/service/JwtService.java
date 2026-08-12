package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.common.config.JwtProperties;
import com.yuanqi.app.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 签发与解析服务。
 * <p>
 * access：短效，携带 userId/uid/username/role；<br>
 * refresh：长效，携带 userId 与 jti，必须配合 auth_session 校验才可换票。
 * </p>
 */
@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;

    public JwtService(JwtProperties jwtProperties, AuthProperties authProperties) {
        this.jwtProperties = jwtProperties;
        this.authProperties = authProperties;
    }

    /** 签发访问令牌（短效） */
    public String createAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("uid", user.getUid());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole() == null ? "USER" : user.getRole());
        claims.put("tokenType", TOKEN_TYPE_ACCESS);
        return buildToken(claims, authProperties.getAccessExpireMs());
    }

    /**
     * 签发刷新令牌。
     *
     * @param user 用户
     * @param jti  会话唯一标识，需与 auth_session.jti 一致
     */
    public String createRefreshToken(User user, String jti) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("uid", user.getUid());
        claims.put("tokenType", TOKEN_TYPE_REFRESH);
        claims.put("jti", jti);
        return buildToken(claims, authProperties.getRefreshExpireMs());
    }

    /** 生成新的 jti */
    public String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 访问令牌剩余有效秒数（用于响应 expiresIn） */
    public long accessExpireSeconds() {
        return Math.max(1L, authProperties.getAccessExpireMs() / 1000L);
    }

    public long refreshExpireMs() {
        return authProperties.getRefreshExpireMs();
    }

    /** 解析 access；无效返回 null */
    public Claims parseAccessClaims(String token) {
        Claims claims = parseClaims(token);
        if (claims == null || !TOKEN_TYPE_ACCESS.equals(claims.get("tokenType", String.class))) {
            return null;
        }
        return claims;
    }

    /** 解析 refresh；无效返回 null */
    public Claims parseRefreshClaims(String token) {
        Claims claims = parseClaims(token);
        if (claims == null || !TOKEN_TYPE_REFRESH.equals(claims.get("tokenType", String.class))) {
            return null;
        }
        return claims;
    }

    public Long readUserId(Claims claims) {
        Object userId = claims.get("userId");
        return userId instanceof Number ? ((Number) userId).longValue() : null;
    }

    public String readRole(Claims claims) {
        String role = claims.get("role", String.class);
        return role == null || role.isBlank() ? "USER" : role;
    }

    public String readJti(Claims claims) {
        return claims.get("jti", String.class);
    }

    /** 兼容旧调用：仅取 access 中的 userId */
    public Long getUserIdFromAccessToken(String token) {
        Claims claims = parseAccessClaims(token);
        return claims == null ? null : readUserId(claims);
    }

    private String buildToken(Map<String, Object> claims, long expireMs) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(signingKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey signingKey() {
        // 使用 JDK SecretKeySpec，避免依赖 io.jsonwebtoken.security.Keys（部分 IDE 索引异常）
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥长度至少 256 bit（32 字节）
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要 32 字节");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
