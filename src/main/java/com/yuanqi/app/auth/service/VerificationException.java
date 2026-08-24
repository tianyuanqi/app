package com.yuanqi.app.auth.service;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult;
import com.yuanqi.app.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class VerificationException extends BusinessException {
    private final ErrorResult.VerificationErrorContext verification;

    public VerificationException(ErrorCode code, boolean retryable, Integer retryAfterSeconds,
                                 ErrorResult.VerificationErrorContext verification) {
        super(code, code.getMessage(), retryable, retryAfterSeconds);
        this.verification = verification;
    }
}
