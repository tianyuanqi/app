package com.yuanqi.app.common.exception;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult;
import com.yuanqi.app.common.context.TraceContext;
import com.yuanqi.app.auth.service.VerificationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** 将所有 JSON 失败统一为 ErrorResult。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResult> handleBusiness(BusinessException e) {
        ErrorResult body = new ErrorResult(e.getErrorCode(), e.getMessage(), e.isRetryable(),
                e.getRetryAfterSeconds(), List.of(), List.of(), null, null, TraceContext.current());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.getErrorCode().getHttpStatus());
        if (e.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return builder.body(body);
    }

    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ErrorResult> handleVerification(VerificationException e) {
        ErrorResult body = new ErrorResult(e.getErrorCode(), e.getMessage(), e.isRetryable(),
                e.getRetryAfterSeconds(), List.of(), List.of(), null, e.getVerification(), TraceContext.current());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.getErrorCode().getHttpStatus());
        if (e.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return builder.body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResult> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResult.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorResult.FieldError(field.getField(), "INVALID", field.getDefaultMessage()))
                .toList();
        ErrorResult body = new ErrorResult(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getMessage(), false, null, fields, List.of(), null, null,
                TraceContext.current());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({BindException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResult> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResult.of(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResult> handlePayloadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.getHttpStatus())
                .body(ErrorResult.of(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResult> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(ErrorResult.of(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResult> handleUnauthorized(AuthenticationException e) {
        return ResponseEntity.status(ErrorCode.AUTH_REQUIRED.getHttpStatus())
                .body(ErrorResult.of(ErrorCode.AUTH_REQUIRED, ErrorCode.AUTH_REQUIRED.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResult> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ErrorResult.of(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResult> handleUnknown(Exception e) {
        log.error("未预期服务异常，traceId={}", TraceContext.current(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ErrorResult.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
