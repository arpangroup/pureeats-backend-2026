package com.pureeats.user.security.ratelimit;

import com.pureeats.user.entity.RateLimitBucket;
import com.pureeats.user.repository.RateLimitBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Split out from {@link DatabaseRateLimiter} purely so the row-creation step runs in its own,
 * separately-committed transaction (via a real Spring-proxied bean-to-bean call) - a same-class
 * self-invocation would silently ignore {@code REQUIRES_NEW} and defeat the whole point of
 * isolating the race from the caller's transaction.
 */
@Service
@RequiredArgsConstructor
class RateLimitBucketStore {

    private final RateLimitBucketRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(String key, LocalDateTime windowStart) {
        if (repository.findByBucketKeyAndWindowStart(key, windowStart).isPresent()) {
            return;
        }
        try {
            RateLimitBucket bucket = new RateLimitBucket();
            bucket.setBucketKey(key);
            bucket.setWindowStart(windowStart);
            bucket.setHitCount(0);
            bucket.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(bucket);
        } catch (DataIntegrityViolationException raceLoser) {
            // Another concurrent request already inserted this window's row - that's fine,
            // tryConsume's own transaction will find and lock it next.
        }
    }
}
