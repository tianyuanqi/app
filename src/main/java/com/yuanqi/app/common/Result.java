package com.yuanqi.app.common;

import lombok.Data;

@Data // Lombok 自动生成 Get/Set
public class Result<T> {
    private Integer code;    // 业务状态码：200 成功，400 参数错误，500 系统异常
    private String message;  // 友好的提示信息
    private T data;          // 真正的业务数据（如果有的话）

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