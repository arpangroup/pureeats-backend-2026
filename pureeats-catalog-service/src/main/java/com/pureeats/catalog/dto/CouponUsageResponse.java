package com.pureeats.catalog.dto;

import java.time.LocalDateTime;

public record CouponUsageResponse(
        Long id,
        String userName,
        String restaurantName,
        Integer couponUsed,
        LocalDateTime createdAt
) {
}
