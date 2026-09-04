package com.yuanqi.app.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证业务可配置项：锁定策略、限流与令牌时长。
 * <p>Access Token 和 Session 时长由本配置提供，JWT 签名密钥取自 app.jwt。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 当前 15 分钟失败窗口内达到该次数后锁定；登录成功会清空失败窗口。 */
    private int maxFailedLogin = 5;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;

    /** 访问令牌有效时长（毫秒），默认 15 分钟，签发时不得超过 Session 剩余期限。 */
    private long accessExpireMs = 900_000L;

    /** Session 有效时长（毫秒），默认七天；签发后绝对期限不随刷新延长。 */
    private long sessionExpireMs = 604_800_000L;

    /** 精确允许的浏览器 Origin。 */
    private List<String> allowedOrigins = new ArrayList<>(List.of("https://localhost:5173"));

    private boolean secureCookies = true;

    /** 限流维度 HMAC Pepper，不得用于 Token 或密码。 */
    private String rateLimitPepper;

    /** 旧实现过渡字段；目标持久化限流不使用这两个单分钟阈值。 */
    @Deprecated
    private int loginRateLimitPerMinute = 20;
    @Deprecated
    private int registerRateLimitPerMinute = 10;

    /** 旧代码过渡别名，最终与固定 Session 期限一致。 */
    @Deprecated
    public long getRefreshExpireMs() {
        return sessionExpireMs;
    }
}
