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

    public record FieldError(
            @Schema(description = "稳定字段路径；数组字段使用零基下标，例如 mediaParameters[2].parameters.captureTime")
            String path,
            @Schema(description = "稳定的字段级校验代码", example = "CAPTURE_TIME_IN_FUTURE") String code,
            String message) {
    }

    public record ItemError(
            @Schema(nullable = true, description = "客户端生成的条目标识；未提供时为 null") String clientItemId,
            @Schema(nullable = true, description = "调用者已知的公开资源 ID；逐图参数错误为公开 mediaId")
            String resourceId,
            @Schema(description = "稳定的条目级错误代码", example = "INVALID_MEDIA_PARAMETERS") String code,
            String message,
            boolean retryable) {
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
