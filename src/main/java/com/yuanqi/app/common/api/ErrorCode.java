package com.yuanqi.app.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码枚举。
 * <p>通用码与认证细粒度码并存；code 写入响应体，httpStatus 决定真实 HTTP 状态。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功", HttpStatus.OK),

    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST),
    /** 对外统一的登录失败文案（防账号枚举） */
    AUTH_BAD_CREDENTIALS(40001, "用户名或密码错误", HttpStatus.BAD_REQUEST),
    AUTH_PASSWORD_POLICY(40002, "密码强度不符合要求", HttpStatus.BAD_REQUEST),
    AUTH_USERNAME_POLICY(40003, "用户名格式不符合要求", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(401, "未登录或令牌无效", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_INVALID(40101, "访问令牌无效或已过期", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_INVALID(40102, "刷新令牌无效或已吊销", HttpStatus.UNAUTHORIZED),
    /** refresh 复用：可能被盗用，已吊销该用户全部会话 */
    AUTH_REFRESH_REUSE(40103, "刷新令牌已被使用，请重新登录", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(403, "无权访问该资源", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_DISABLED(40301, "账号已被禁用", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_LOCKED(40302, "账号已锁定，请稍后再试", HttpStatus.FORBIDDEN),

    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),

    CONFLICT(409, "资源冲突", HttpStatus.CONFLICT),
    AUTH_USERNAME_EXISTS(40901, "用户名已存在", HttpStatus.CONFLICT),
    AUTH_EMAIL_EXISTS(40902, "邮箱已被注册", HttpStatus.CONFLICT),

    TOO_MANY_REQUESTS(429, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    AUTH_RATE_LIMITED(42901, "登录或注册过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(500, "系统开小差了，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
