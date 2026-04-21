package com.yuanqi.app.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data // Lombok 自动生成 Get/Set
@Schema(description = "统一响应包装对象") // 描述这个类是做什么的
public class Result<T> {

    @Schema(description = "业务状态码", example = "200") // 200 成功，400 参数错误，500 系统异常
    private Integer code;

    @Schema(description = "提示信息", example = "操作成功")
    private String message;  // 友好的提示信息

    @Schema(description = "业务数据体") // 真正的业务数据(如果有的话)   这里是泛型 T，Swagger 会自动关联具体的业务对象
    private T data;

    // 私有构造函数，强制大家使用下面的静态方法
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 快捷方法：请求成功时调用
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    // 快捷方法：请求失败时调用
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}