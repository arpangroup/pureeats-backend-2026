package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponResponse(
        Long id,
        String name,
        String description,
        String code,
        /** "flat", "percentage" or "free_delivery". */
        String discountType,
        BigDecimal discount,
        BigDecimal minOrderAmount,
        BigDecimal uptoAmount,
        LocalDate expiryDate,
        boolean isActive,
        Integer restaurantId,
        boolean firstOrderOnly,
        Integer totalCoupon,
        Integer count,
        Integer maxCount,
        Long createdBy
) {
}
