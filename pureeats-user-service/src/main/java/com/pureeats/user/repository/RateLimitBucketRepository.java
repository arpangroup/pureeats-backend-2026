package com.pureeats.user.repository;

import com.pureeats.user.entity.RateLimitBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucket, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RateLimitBucket> findByBucketKeyAndWindowStart(String bucketKey, LocalDateTime windowStart);
}
