package com.pureeats.catalog.dto;

import java.math.BigDecimal;

public record RestaurantSummaryResponse(
        Long id,
        String name,
        String slug,
        String image,
        String rating,
        String deliveryTime,
        String priceRange,
        boolean isPureveg,
        boolean isActive,
        boolean isAccepted,
        BigDecimal minOrderPrice,
        BigDecimal deliveryCharges
) {
}
