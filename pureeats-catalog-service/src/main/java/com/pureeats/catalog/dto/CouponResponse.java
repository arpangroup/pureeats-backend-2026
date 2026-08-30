package com.pureeats.catalog.dto;

import com.pureeats.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponResponse(
        Long id,
        String name,
        String description,
        String code,
        DiscountType discountType,
        BigDecimal discount,
        BigDecimal minOrderAmount,
        BigDecimal uptoAmount,
        LocalDate expiryDate,
        boolean isActive,
        Integer restaurantId
) {
}
