package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易内存限流器（按 IP + 动作）。
 * <p>单机靶场足够；多实例部署需改为 Redis，本阶段不引入。</p>
 */
@Component
public class AuthRateLimiter {

    private final AuthProperties authProperties;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /** 校验登录限流，超限抛出 42901 */
    public void checkLogin(String ip) {
        check("login:" + normalizeIp(ip), authProperties.getLoginRateLimitPerMinute());
    }

    /** 校验注册限流 */
    public void checkRegister(String ip) {
        check("register:" + normalizeIp(ip), authProperties.getRegisterRateLimitPerMinute());
    }

    private void check(String key, int limitPerMinute) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> deque = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.removeFirst();
            }
            if (deque.size() >= limitPerMinute) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            deque.addLast(now);
        }
    }

    private String normalizeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
