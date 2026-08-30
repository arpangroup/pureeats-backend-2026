package com.pureeats.user.security.ratelimit;

import com.pureeats.user.entity.RateLimitBucket;
import com.pureeats.user.repository.RateLimitBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Fixed-window counter, one row per (key, window start), mutated under a pessimistic row lock so
 * concurrent requests for the same key never both slip through.
 */
@Service
@RequiredArgsConstructor
public class DatabaseRateLimiter implements RateLimiter {

    private final RateLimitBucketRepository repository;
    private final RateLimitBucketStore bucketStore;

    @Override
    @Transactional
    public boolean tryConsume(String key, int limit, Duration window) {
        LocalDateTime windowStart = flooredWindowStart(window);
        bucketStore.ensureExists(key, windowStart);

        RateLimitBucket bucket = repository.findByBucketKeyAndWindowStart(key, windowStart)
                .orElseThrow(() -> new IllegalStateException("Rate limit bucket vanished after ensureExists"));

        if (bucket.getHitCount() >= limit) {
            return false;
        }
        bucket.setHitCount(bucket.getHitCount() + 1);
        bucket.setUpdatedAt(LocalDateTime.now());
        repository.save(bucket);
        return true;
    }

    private LocalDateTime flooredWindowStart(Duration window) {
        long windowSeconds = Math.max(1, window.getSeconds());
        long epochSeconds = Instant.now().getEpochSecond();
        long flooredEpoch = (epochSeconds / windowSeconds) * windowSeconds;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(flooredEpoch), ZoneOffset.UTC);
    }
}
