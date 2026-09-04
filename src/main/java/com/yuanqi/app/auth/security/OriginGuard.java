package com.yuanqi.app.auth.security;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** 对认证入口执行 Origin 精确白名单校验，补充 Cookie 场景的来源约束。 */
@Component
public class OriginGuard {
    private final AuthProperties properties;

    public OriginGuard(AuthProperties properties) {
        this.properties = properties;
    }

    /** 缺少 Origin 也拒绝，不使用 Referer 回退；通过本检查不代表已认证或已通过 CSRF 校验。 */
    public void requireTrusted(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !properties.getAllowedOrigins().contains(origin)) {
            throw new BusinessException(ErrorCode.ORIGIN_NOT_ALLOWED);
        }
    }
}
