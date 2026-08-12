package com.yuanqi.app.auth.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求中提取客户端信息（IP / UA），供审计与会话落库使用。
 */
public final class ClientInfo {

    private ClientInfo() {
    }

    /**
     * 提取客户端 IP。
     * <p>优先 X-Forwarded-For 首段（代理场景），否则用 remoteAddr。</p>
     */
    public static String ip(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
