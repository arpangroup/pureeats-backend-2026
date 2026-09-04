package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

public record RestaurantSummaryResponse(
        Long id,
        String name,
        String slug,
        String image,
        String rating,
        Integer deliveryTime,
        String priceRange,
        boolean isPureveg,
        boolean isActive,
        boolean isAccepted,
        BigDecimal minOrderPrice,
        BigDecimal deliveryCharges,
        /** Needed on card grids (Home/Search/category listing/Top Picks), not just the detail page, so a closed-right-now restaurant can be grayed out everywhere it's shown — see isRestaurantOrderable on the client. */
        LocalTime openingTime,
        LocalTime closingTime,
        boolean isFeatured
) {
}
