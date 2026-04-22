package com.yuanqi.app.common;

import io.jsonwebtoken.Claims;
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


    // 👇 新增：从 Token 中解析出 userId 的方法
    public static Long getUserIdFromToken(String token) {
        try {
            // 工业级解密：用我们的 SECRET 去验证这把锁有没有被篡改过
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token)
                    .getBody();

            // 提取出我们在 createToken 时塞进去的 userId，注意强转类型
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            // 如果 token 过期、被篡改、或者格式不对，都会抛异常走到这里
            return null;
        }
    }
}