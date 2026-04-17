package com.yuanqi.app.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {
    // 签名密钥（工业级做法应放在配置文件中）
    private static final String SECRET = "Yuanqi_Photo_Secret_Key_2026";
    // 过期时间：24 小时
    private static final long EXPIRE = 60 * 60 * 24 * 1000;

    // 生成 Token
    public static String createToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }
}