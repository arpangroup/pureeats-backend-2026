package com.pureeats.domain.common;

/**
 * Mirrors {@link CurrentUserContext}: the correlation/request id (from an inbound
 * {@code X-Request-ID} header, or generated if absent) is set once per request by a servlet
 * filter in pureeats-app and read from here by any module that wants to stamp it onto an audit
 * row, notification log or error response - without a servlet/framework dependency.
 */
public final class RequestIdContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestIdContext() {
    }

    public static void set(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String get() {
        return REQUEST_ID.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
