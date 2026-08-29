package com.pureeats.user.security.ratelimit;

import com.pureeats.domain.common.exception.TooManyRequestsException;

import java.time.Duration;

/**
 * Fixed-window rate limiter. {@code key} should already encode the dimension and endpoint, e.g.
 * {@code "otp-login:ip:203.0.113.4"}. The default implementation is DB-backed (see
 * {@link DatabaseRateLimiter}) so it's correct across multiple app instances without Redis; swap
 * in a Redis-backed implementation later without touching any caller.
 */
public interface RateLimiter {

    boolean tryConsume(String key, int limit, Duration window);

    default void enforce(String key, int limit, Duration window, String message) {
        if (!tryConsume(key, limit, window)) {
            throw new TooManyRequestsException(message);
        }
    }
}
