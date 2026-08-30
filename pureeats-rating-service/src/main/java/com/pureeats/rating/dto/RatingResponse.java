package com.pureeats.rating.dto;

import java.time.LocalDateTime;

public record RatingResponse(
        Long id,
        Long orderId,
        RateableType rateableType,
        Long rateableId,
        int rating,
        String comment,
        String tags,
        String raterName,
        LocalDateTime createdAt
) {
}
