package com.yuanqi.app.auth.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 生成客户端不可解析的稳定公开 ID。 */
@Component
public class PublicIdGenerator {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next() {
        char[] value = new char[26];
        for (int i = 0; i < value.length; i++) {
            value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}
