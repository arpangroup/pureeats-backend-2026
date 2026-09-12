package com.pureeats.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record RestaurantUpdateRequest(
        @NotBlank String name,
        String description,
        @NotBlank String contactNumber,
        @NotNull LocalTime openingTime,
        @NotNull LocalTime closingTime,
        String image,
        @NotBlank String address,
        String pincode,
        String landmark,
        @NotNull BigDecimal deliveryCharges,
        /** Capped well above any real food-delivery range - guards against an accidental typo (e.g. "700" or "5011" meant to be "7"/"5") silently letting orders hundreds of km away through as "in range". */
        @NotNull @DecimalMin("0") @DecimalMax(RestaurantValidation.MAX_DELIVERY_RADIUS_KM) BigDecimal deliveryRadius,
        @NotNull BigDecimal minOrderPrice,
        boolean isAcceptCod,
        boolean autoAcceptable,
        /** Purely informational capability badge - independent of actual order-fulfillment (deliveryType). */
        boolean isDineInAvailable
) {
}
