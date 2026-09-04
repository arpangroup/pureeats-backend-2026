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
        /**
         * Legacy single-window fields - kept for backward compatibility, but superseded by
         * {@code openStatus} below for actually deciding open/closed. Never day-aware: derived
         * client-side from "whichever day comes first in the week", not "today" (see
         * RESTAURANT_DOMAIN_ARCHITECTURE.md §4-5). Do not use these two to compute open/closed.
         */
        LocalTime openingTime,
        LocalTime closingTime,
        boolean isFeatured,
        /** The real-time, day-aware answer — computed server-side from the restaurant's actual weeklySchedule. Use this, not openingTime/closingTime, for any "is it open" / grey-out logic. */
        RestaurantOpenStatus openStatus
) {
}
