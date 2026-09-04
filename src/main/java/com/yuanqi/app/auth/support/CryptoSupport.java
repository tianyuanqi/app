package com.yuanqi.app.auth.support;

import com.yuanqi.app.auth.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class CryptoSupport {
    private final byte[] pepper;

    public CryptoSupport(AuthProperties properties) {
        String configured = properties.getRateLimitPepper();
        if (configured == null || configured.length() < 16) {
            throw new IllegalStateException("AUTH_RATE_LIMIT_PEPPER 至少需要 16 个字符");
        }
        this.pepper = configured.getBytes(StandardCharsets.UTF_8);
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    public boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
