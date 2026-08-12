package com.yuanqi.app.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 相关配置，密钥与过期时间由环境变量 / profile 注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC 签名密钥，长度建议不少于 32 字节 */
    private String secret;

    /** 访问令牌过期毫秒数，默认 24 小时 */
    private long accessExpireMs = 86_400_000L;

    /** 刷新令牌过期毫秒数，默认 30 天 */
    private long refreshExpireMs = 2_592_000_000L;
}
