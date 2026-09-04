package com.yuanqi.app.common.exception;

import com.yuanqi.app.common.api.ErrorCode;
import lombok.Getter;

/**
 * 可预期的业务异常，由全局异常处理器转换为统一 JSON 与 HTTP 状态码。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), false, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, false, null);
    }

    public BusinessException(ErrorCode errorCode, String message, boolean retryable, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
