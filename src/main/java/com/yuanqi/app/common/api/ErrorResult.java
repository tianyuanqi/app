package com.yuanqi.app.common.api;

import com.yuanqi.app.common.context.TraceContext;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** 统一失败响应。 */
@Schema(name = "ErrorResult", description = "统一失败响应")
public record ErrorResult(
        ErrorCode code,
        String message,
        boolean retryable,
        Integer retryAfterSeconds,
        List<FieldError> fieldErrors,
        List<ItemError> itemErrors,
        ConflictView conflict,
        VerificationErrorContext verification,
        String traceId) {

    public static ErrorResult of(ErrorCode code, String message) {
        return new ErrorResult(code, message, false, null, List.of(), List.of(), null, null,
                TraceContext.current());
    }

    public record FieldError(String path, String code, String message) {
    }

    public record ItemError(String clientItemId, String resourceId, String code, String message, boolean retryable) {
    }

    public record ConflictView(String resourceType, String currentVersionTag, boolean reloadRequired) {
    }

    public record VerificationErrorContext(
            int attemptsRemaining,
            OffsetDateTime flowExpiresAt,
            OffsetDateTime retryAvailableAt,
            boolean newFlowRequired) {
    }
}
