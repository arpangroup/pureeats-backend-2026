package com.pureeats.catalog.dto;

import com.pureeats.domain.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponUpdateRequest(
        @NotBlank String name,
        String description,
        @NotBlank String code,
        @NotNull DiscountType discountType,
        @NotNull @Positive BigDecimal discount,
        @NotNull BigDecimal minOrderAmount,
        @NotNull BigDecimal uptoAmount,
        LocalDate expiryDate,
        /** 0/null = global coupon, otherwise scoped to this restaurant. */
        Integer restaurantId,
        @NotNull @Positive Integer totalCoupon,
        @NotNull @Positive Integer maxCount,
        boolean isActive
) {
}
