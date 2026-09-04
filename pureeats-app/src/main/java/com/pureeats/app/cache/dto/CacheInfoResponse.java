package com.pureeats.app.cache.dto;

/** {@code estimatedSize} is null when the active {@link org.springframework.cache.CacheManager} isn't backed by Caffeine (e.g. a future Redis provider) - sizing just isn't exposed generically by Spring's cache abstraction. */
public record CacheInfoResponse(String name, Long estimatedSize) {
}
