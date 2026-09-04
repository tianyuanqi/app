package com.yuanqi.app.common.context;

import java.util.UUID;

/** 请求级不透明追踪标识。 */
public final class TraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String current() {
        String traceId = TRACE_ID.get();
        return traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
