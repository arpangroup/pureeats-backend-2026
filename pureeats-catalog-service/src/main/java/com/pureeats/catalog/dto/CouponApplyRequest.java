package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CouponApplyRequest(
        @NotBlank String code,
        @NotNull Integer restaurantId,
        @NotNull @Positive BigDecimal orderAmount
) {
}
