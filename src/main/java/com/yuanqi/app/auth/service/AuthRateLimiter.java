package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.entity.RateLimitBucket;
import com.yuanqi.app.auth.mapper.RateLimitBucketMapper;
import com.yuanqi.app.auth.support.CryptoSupport;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 数据库持久化、去敏维度的认证限流。 */
@Service
public class AuthRateLimiter {
    private final RateLimitBucketMapper mapper;
    private final CryptoSupport crypto;
    private final Clock clock;

    public AuthRateLimiter(RateLimitBucketMapper mapper, CryptoSupport crypto, Clock clock) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Transactional
    public void checkVerificationSend(String emailKey, String ip) {
        consume("verification-email", emailKey, 600, 5);
        consume("verification-email-day", emailKey, 86_400, 20);
        consume("verification-ip", normalizedIp(ip), 600, 20);
        consume("verification-ip-day", normalizedIp(ip), 86_400, 100);
    }

    @Transactional
    public void checkRegistration(String emailKey, String ip) {
        consume("register-email", emailKey, 600, 5);
        consume("register-ip", normalizedIp(ip), 3_600, 20);
    }

    @Transactional
    public void checkLogin(String emailKey, String ip) {
        consume("login-pair", emailKey + "\n" + normalizedIp(ip), 900, 10);
        consume("login-ip", normalizedIp(ip), 900, 50);
    }

    public LocalDateTime nextVerificationSendAt(String emailKey, String ip) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime result = now;
        result = later(result, next("verification-email", emailKey, 5, now));
        result = later(result, next("verification-email-day", emailKey, 20, now));
        result = later(result, next("verification-ip", normalizedIp(ip), 20, now));
        result = later(result, next("verification-ip-day", normalizedIp(ip), 100, now));
        return result;
    }

    private LocalDateTime next(String action, String identity, int limit, LocalDateTime now) {
        return mapper.nextAllowedAt(crypto.hmac(action + ":" + identity), action, limit, now);
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        return right != null && right.isAfter(left) ? right : left;
    }

    private void consume(String action, String identity, int windowSeconds, int limit) {
        Instant nowInstant = clock.instant();
        long epoch = nowInstant.getEpochSecond();
        long startEpoch = epoch - Math.floorMod(epoch, windowSeconds);
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochSecond(startEpoch), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
        String bucketKey = crypto.hmac(action + ":" + identity);
        RateLimitBucket bucket = mapper.findForUpdate(bucketKey, action, start, windowSeconds);
        if (bucket != null && bucket.getRequestCount() >= limit) {
            int retry = (int) Math.max(1, startEpoch + windowSeconds - epoch);
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getMessage(), true, retry);
        }
        if (bucket == null) {
            mapper.insert(bucketKey, action, start, windowSeconds, now);
        } else {
            mapper.increment(bucketKey, action, start, windowSeconds, now);
        }
    }

    private String normalizedIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
