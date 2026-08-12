package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.entity.AuthAuditLog;
import com.yuanqi.app.auth.mapper.AuthAuditLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证审计服务：异步感不强，同步写入即可满足靶场与排障需求。
 */
@Service
public class AuthAuditService {

    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAILED = "REGISTER_FAILED";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    public static final String REFRESH_SUCCESS = "REFRESH_SUCCESS";
    public static final String REFRESH_FAILED = "REFRESH_FAILED";
    public static final String REFRESH_REUSE = "REFRESH_REUSE";
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private final AuthAuditLogMapper authAuditLogMapper;

    public AuthAuditService(AuthAuditLogMapper authAuditLogMapper) {
        this.authAuditLogMapper = authAuditLogMapper;
    }

    /**
     * 记录一条认证审计。
     *
     * @param userId    可为空（例如用户名不存在时）
     * @param eventType 事件类型常量
     * @param success   是否业务成功
     * @param ip        客户端 IP
     * @param userAgent UA
     * @param detail    补充说明，禁止写入密码明文
     */
    public void record(Long userId, String eventType, boolean success,
                       String ip, String userAgent, String detail) {
        AuthAuditLog log = new AuthAuditLog();
        log.setUserId(userId);
        log.setEventType(eventType);
        log.setSuccess(success);
        log.setIp(truncate(ip, 64));
        log.setUserAgent(truncate(userAgent, 512));
        log.setDetail(truncate(detail, 512));
        log.setCreatedAt(LocalDateTime.now());
        authAuditLogMapper.insert(log);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
