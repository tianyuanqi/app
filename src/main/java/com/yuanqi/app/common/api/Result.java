package com.yuanqi.app.common.api;

import com.yuanqi.app.common.context.TraceContext;
import io.swagger.v3.oas.annotations.media.Schema;

/** 统一成功响应；二进制媒体响应不使用本包装。 */
@Schema(description = "统一成功响应")
public record Result<T>(
        @Schema(example = "OK") String code,
        String message,
        T data,
        String traceId) {

    public static <T> Result<T> success(T data) {
        return new Result<>("OK", "操作成功", data, TraceContext.current());
    }
}
