package com.yuanqi.app.auth.support;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@Profile("!test")
public class SecureVerificationCodeGenerator implements VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateSixDigits() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
