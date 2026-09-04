package com.yuanqi.app.common.api;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** v1.0 目标契约允许返回的闭合错误码。 */
@Getter
public enum ErrorCode {
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "访问令牌已过期"),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "账号已锁定"),
    ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "账号当前不可用"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF 校验失败"),
    DYNAMIC_IMAGE(HttpStatus.UNPROCESSABLE_ENTITY, "不支持动态图片"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "邮箱已被注册"),
    FILE_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "文件大小超出限制"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行该操作"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "相同幂等请求正在处理"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "缺少 Idempotency-Key"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key 已用于不同请求"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统开小差了，请稍后再试"),
    INVALID_CONTENT(HttpStatus.UNPROCESSABLE_ENTITY, "文件内容无效"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "分页游标无效"),
    INVALID_FILTER(HttpStatus.BAD_REQUEST, "筛选参数无效"),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Idempotency-Key 格式无效"),
    INVALID_IF_MATCH(HttpStatus.BAD_REQUEST, "If-Match 格式无效"),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "页码无效"),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "分页大小无效"),
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "查询参数无效"),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "排序参数无效"),
    MAIL_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "邮件暂时无法发送"),
    ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "请求来源不受信任"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "请求体过大"),
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, "资源版本已变化"),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "缺少 If-Match"),
    PROCESSING_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "媒体处理失败"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "刷新凭证已被重复使用"),
    REGISTRATION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "注册已经完成"),
    RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "暂时不能重新发送"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不可用"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用"),
    SESSION_INVALID(HttpStatus.UNAUTHORIZED, "登录会话已失效"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "资源状态已变化"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "媒体存储暂时不可用"),
    TARGET_NOT_GOVERNABLE(HttpStatus.CONFLICT, "目标账号不可治理"),
    UNSUPPORTED_FORMAT(HttpStatus.UNPROCESSABLE_ENTITY, "文件格式不受支持"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "媒体类型不受支持"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    VERIFICATION_CODE_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "验证码尝试次数已用完"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "验证码已过期"),
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "验证码错误"),
    VERIFICATION_FLOW_CONSUMED(HttpStatus.CONFLICT, "验证流程已结束");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @JsonValue
    public String value() {
        return name();
    }
}
