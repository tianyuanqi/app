package com.yuanqi.app.auth.security;

import com.yuanqi.app.auth.support.CryptoSupport;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/** 生成带 HMAC 的会话绑定 CSRF Token；请求头与 Cookie 的一致性由 Controller 校验。 */
@Service
public class CsrfTokenService {
    private final CryptoSupport crypto;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CsrfTokenService(CryptoSupport crypto, Clock clock) {
        this.crypto = crypto;
        this.clock = clock;
    }

    /** 将公开会话标识、UTC 到期秒数和随机 nonce 签名，不在服务端保存 Token。 */
    public String issue(String sessionId, LocalDateTime expiresAt) {
        byte[] nonce = new byte[24];
        random.nextBytes(nonce);
        String payload = sessionId + "." + expiresAt.toEpochSecond(ZoneOffset.UTC) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + crypto.hmac(payload);
    }

    /** 校验会话绑定、签名及到期秒数；恰好到期或格式异常均返回 false，不查询会话状态。 */
    public boolean valid(String token, String sessionId) {
        if (token == null) {
            return false;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            String signature = token.substring(dot + 1);
            String[] parts = payload.split("\\.", 3);
            return parts.length == 3 && parts[0].equals(sessionId)
                    && Long.parseLong(parts[1]) > clock.instant().getEpochSecond()
                    && crypto.constantTimeEquals(crypto.hmac(payload), signature);
        } catch (Exception e) {
            return false;
        }
    }
}
