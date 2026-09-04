package com.yuanqi.app.common.exception;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult;

import java.util.List;

/** 携带稳定字段和数组项定位的请求校验失败。 */
public class RequestValidationException extends BusinessException {
    private final List<ErrorResult.FieldError> fieldErrors;
    private final List<ErrorResult.ItemError> itemErrors;

    public RequestValidationException(List<ErrorResult.FieldError> fieldErrors,
                                      List<ErrorResult.ItemError> itemErrors) {
        super(ErrorCode.VALIDATION_FAILED);
        this.fieldErrors = List.copyOf(fieldErrors);
        this.itemErrors = List.copyOf(itemErrors);
    }

    public List<ErrorResult.FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public List<ErrorResult.ItemError> getItemErrors() {
        return itemErrors;
    }
}
