package com.yuanqi.app.auth.security;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class OriginGuard {
    private final AuthProperties properties;

    public OriginGuard(AuthProperties properties) {
        this.properties = properties;
    }

    public void requireTrusted(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !properties.getAllowedOrigins().contains(origin)) {
            throw new BusinessException(ErrorCode.ORIGIN_NOT_ALLOWED);
        }
    }
}
