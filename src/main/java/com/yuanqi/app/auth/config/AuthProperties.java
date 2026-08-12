package com.yuanqi.app.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证业务可配置项：锁定策略、限流与令牌时长。
 * <p>令牌时长优先读本配置；若未单独配置则回退到 app.jwt。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 连续登录失败达到该次数后锁定账号 */
    private int maxFailedLogin = 5;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;

    /** 同一 IP 每分钟允许的登录次数 */
    private int loginRateLimitPerMinute = 20;

    /** 同一 IP 每分钟允许的注册次数 */
    private int registerRateLimitPerMinute = 10;

    /** 访问令牌过期毫秒数，默认 30 分钟 */
    private long accessExpireMs = 1_800_000L;

    /** 刷新令牌过期毫秒数，默认 14 天 */
    private long refreshExpireMs = 1_209_600_000L;
}
