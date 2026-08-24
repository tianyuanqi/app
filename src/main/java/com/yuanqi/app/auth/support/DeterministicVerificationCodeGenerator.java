package com.yuanqi.app.auth.support;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** 仅隔离 Test Profile 装配，不进入生产随机源。 */
@Component
@Profile("test")
public class DeterministicVerificationCodeGenerator implements VerificationCodeGenerator {
    private final AtomicInteger sequence = new AtomicInteger(100000);
    @Override public String generateSixDigits() { return String.format("%06d", sequence.getAndUpdate(v -> v >= 999999 ? 100000 : v + 1)); }
}
