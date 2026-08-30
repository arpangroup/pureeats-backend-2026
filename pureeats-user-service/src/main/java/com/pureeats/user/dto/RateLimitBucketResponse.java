package com.pureeats.user.dto;

import java.time.LocalDateTime;

public record RateLimitBucketResponse(
        Long id,
        String bucketKey,
        LocalDateTime windowStart,
        Integer hitCount,
        LocalDateTime updatedAt
) {
}
