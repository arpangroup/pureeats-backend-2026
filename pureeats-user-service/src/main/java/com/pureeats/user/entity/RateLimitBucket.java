package com.pureeats.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Fixed-window counter backing the DB-based {@code RateLimiter}. One row per (bucket key, window
 * start); {@code bucketKey} already encodes the dimension (ip/user/phone/email/device) and the
 * endpoint, e.g. {@code "login:ip:203.0.113.4"}. Deliberately a plain counter table - not the
 * final word on rate limiting at high scale, but portable (MySQL/Postgres/H2) and correct under
 * concurrency via pessimistic row locking; swap the {@code RateLimiter} implementation for a
 * Redis-backed one later without touching any caller.
 */
@Entity
@Table(name = "rate_limit_buckets", indexes = {
        @Index(name = "idx_rate_limit_buckets_key_window", columnList = "bucket_key, window_start", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "bucket_key", nullable = false, length = 191)
    private String bucketKey;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
