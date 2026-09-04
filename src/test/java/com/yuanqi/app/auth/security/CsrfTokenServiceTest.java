package com.yuanqi.app.auth.security;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.support.CryptoSupport;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfTokenServiceTest {
    @Test
    void token绑定session并在绝对期限后失效() {
        AuthProperties properties = new AuthProperties();
        properties.setRateLimitPepper("test-pepper-at-least-32-characters-long");
        Clock issuedClock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
        CsrfTokenService issued = new CsrfTokenService(new CryptoSupport(properties), issuedClock);
        LocalDateTime expires = LocalDateTime.ofInstant(issuedClock.instant().plusSeconds(60), ZoneOffset.UTC);
        String token = issued.issue("session-a", expires);
        assertTrue(issued.valid(token, "session-a"));
        assertFalse(issued.valid(token, "session-b"));
        Clock expiredClock = Clock.fixed(issuedClock.instant().plusSeconds(61), ZoneOffset.UTC);
        assertFalse(new CsrfTokenService(new CryptoSupport(properties), expiredClock).valid(token, "session-a"));
    }
}
