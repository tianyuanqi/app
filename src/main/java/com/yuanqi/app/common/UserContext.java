package com.yuanqi.app.common;


/**
 * 线程上下文工具类：用于存储当前登录用户的 ID
 */
public class UserContext {

    private static final ThreadLocal<Long> thread_local = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        thread_local.set(userId);
    }

    public static Long getUserId() {

       return thread_local.get();
    }

    // 必须：请求结束时清理内存，防止内存泄漏
    public static void remove() {
        thread_local.remove();
    }
}
