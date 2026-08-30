package com.pureeats.catalog.dto;

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
        @NotNull BigDecimal deliveryRadius,
        @NotNull BigDecimal minOrderPrice,
        boolean isAcceptCod,
        boolean autoAcceptable
) {
}
