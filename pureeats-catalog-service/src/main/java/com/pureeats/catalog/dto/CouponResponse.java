package com.pureeats.catalog.dto;

import com.pureeats.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        String name,
        String description,
        String code,
        DiscountType discountType,
        BigDecimal discount,
        BigDecimal minOrderAmount,
        BigDecimal uptoAmount,
        LocalDateTime expiryDate,
        boolean isActive,
        Integer restaurantId
) {
}
