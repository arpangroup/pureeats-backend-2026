package com.pureeats.app.cache.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.pureeats.app.cache.dto.CacheClearResponse;
import com.pureeats.app.cache.dto.CacheInfoResponse;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every {@code @Cacheable} across every module binds to the one app-wide {@link CacheManager}
 * (see {@code CacheConfig} in this same module) - so clearing every cache here, generically, by
 * iterating {@code cacheManager.getCacheNames()}, covers restaurants, menus, coupons, rider
 * profiles, etc. without this controller needing to know their names ahead of time. A backend
 * write outside this admin panel (a direct DB edit, another service, a support script) can leave a
 * stale cached read behind for up to the cache's TTL - this gives an admin a way to force it fresh
 * immediately instead of waiting.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Cache", description = "Inspect and clear the app's caches - ADMIN or SUPER_ADMIN only")
public class AdminCacheController {

    private final CacheManager cacheManager;

    @GetMapping("/api/v1/admin/cache")
    @Operation(summary = "List every active cache and its current (estimated) size")
    public ApiResponse<List<CacheInfoResponse>> list() {
        List<CacheInfoResponse> caches = cacheManager.getCacheNames().stream()
                .sorted()
                .map(name -> new CacheInfoResponse(name, estimatedSize(name)))
                .toList();
        return ApiResponse.success(caches);
    }

    @PostMapping("/api/v1/admin/cache/clear")
    @Operation(summary = "Clear every cache so the next request re-reads straight from the database")
    public ApiResponse<CacheClearResponse> clearAll() {
        List<String> names = cacheManager.getCacheNames().stream().sorted().toList();
        names.forEach(this::clearByName);
        log.info("Admin cleared {} cache(s): {}", names.size(), names);
        return ApiResponse.success("Cleared " + names.size() + " cache" + (names.size() == 1 ? "" : "s"), new CacheClearResponse(names));
    }

    @PostMapping("/api/v1/admin/cache/{name}/clear")
    @Operation(summary = "Clear one specific cache by name")
    public ApiResponse<Void> clearOne(@PathVariable String name) {
        if (cacheManager.getCache(name) == null) {
            throw new ResourceNotFoundException("No such cache: " + name);
        }
        clearByName(name);
        log.info("Admin cleared cache '{}'", name);
        return ApiResponse.success("Cache cleared", null);
    }

    private void clearByName(String name) {
        var cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }

    private Long estimatedSize(String name) {
        var cache = cacheManager.getCache(name);
        if (cache == null) {
            return null;
        }
        Object nativeCache = cache.getNativeCache();
        return nativeCache instanceof Cache<?, ?> caffeine ? caffeine.estimatedSize() : null;
    }
}
