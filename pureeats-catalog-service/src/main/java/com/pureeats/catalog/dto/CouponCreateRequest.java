package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CouponCreateRequest(
        @NotBlank String name,
        String description,
        @NotBlank String code,
        /** "flat", "percentage" or "free_delivery" - see {@link com.pureeats.catalog.service.CouponService#toDiscountType}. */
        @NotBlank String discountType,
        /** Ignored for "free_delivery" (which waives the charge outright, not a rupee/percent amount). */
        BigDecimal discount,
        @NotNull BigDecimal minOrderAmount,
        /** Ignored for "free_delivery". */
        BigDecimal uptoAmount,
        LocalDate expiryDate,
        /** 0/null = global coupon, otherwise scoped to this restaurant. */
        Integer restaurantId,
        @NotNull @Positive Integer totalCoupon,
        /** Usable only on the customer's first order. */
        Boolean firstOrderOnly
) {
}
