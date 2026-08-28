package com.pureeats.domain.common;

/**
 * The security layer sets:
 *      CurrentUserContext.set(userId);
 * at the beginning of the request and clears it afterward.
 */
public final class CurrentUserContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
