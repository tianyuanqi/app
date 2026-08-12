package com.yuanqi.app.common.context;

/**
 * 当前请求用户上下文（基于 ThreadLocal）。
 * <p>由 JWT 过滤器写入，请求结束必须清理，避免线程池脏数据。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 请求结束时清理，防止线程复用污染 */
    public static void remove() {
        USER_ID.remove();
    }
}
