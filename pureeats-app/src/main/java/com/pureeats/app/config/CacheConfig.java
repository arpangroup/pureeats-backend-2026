package com.pureeats.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Registers the app-wide {@link CacheManager} that every {@code @Cacheable}/{@code @CacheEvict} method
 * across every module (restaurants/menus/coupons in catalog-service, rider profiles in user-service, ...)
 * binds to - callers never see the concrete cache technology, only Spring's cache abstraction, so
 * swapping the backing store later is a config change here, not a code change anywhere else.
 * <p>
 * Today ({@code pureeats.cache.provider=caffeine}, the default) this is a per-instance in-memory cache -
 * fine for a single-node deployment, and lazily creates a new named cache on first use (so a brand new
 * {@code @Cacheable(cacheNames = "...")} elsewhere in the codebase never needs a matching change here).
 * When the app is scaled horizontally, flipping {@code pureeats.cache.provider=redis} (once a
 * {@code RedisCacheManager} bean is added following this exact same {@code @ConditionalOnProperty}
 * shape) would give every instance a shared, consistent view of the cache with zero change to any
 * {@code @Cacheable} call site.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(prefix = "pureeats.cache", name = "provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager(
            @Value("${pureeats.cache.ttl-minutes:30}") long ttlMinutes,
            @Value("${pureeats.cache.max-entries:5000}") long maxEntries) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxEntries));
        // Names are NOT pre-declared: allowNullValues + no setCacheNames(...) means any cache name a
        // @Cacheable method asks for is created on demand, so a new cacheable method never needs a
        // matching edit here.
        return manager;
    }
}
