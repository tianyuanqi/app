package com.yuanqi.app.exception;

import com.yuanqi.app.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 核心注解：启动全局拦截
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 专门拦截我们在 Service 里抛出的 IllegalArgumentException (比如没传文件)
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常拦截: {}", e.getMessage());
        // 包装成 400 状态码返回给前端
        return Result.error(400, "哎呀，填写的参数有点问题：" + e.getMessage());
    }

    // 兜底拦截：抓住所有我们没预料到的 Exception (比如数据库断了、空指针等)
    @ExceptionHandler(Exception.class)
    public Result<String> handleAllException(Exception e) {
        log.error("系统发生未知严重异常", e); // 后台打印堆栈，方便排查
        // 包装成 500 状态码，对外绝对不暴露代码细节
        return Result.error(500, "系统开小差了，请稍后再试");
    }
}