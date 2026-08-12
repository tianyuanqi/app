package com.yuanqi.app.common.exception;

import com.yuanqi.app.common.api.ErrorCode;
import lombok.Getter;

/**
 * 可预期的业务异常，由全局异常处理器转换为统一 JSON 与 HTTP 状态码。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
