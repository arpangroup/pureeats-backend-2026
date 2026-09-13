package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

public record RestaurantSummaryResponse(
        Long id,
        String name,
        String slug,
        String image,
        /** Static, admin-set trust signal (e.g. 4.5) - not derived from real customer reviews. */
        BigDecimal rating,
        /** A real distance-based ETA when the request carried the customer's lat/lng (see distanceKm below); otherwise this restaurant's own static admin-set prep+delivery estimate, unchanged from before. */
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
        /** Admin/store-owner-set promo badge fields - both null means no badge should render (see restaurantOfferBadge() on the customer app). */
        Integer offerDiscountPercent,
        BigDecimal offerMaxDiscount,
        /** Purely informational - whether this restaurant also seats walk-in/dine-in customers. Independent of actual order-fulfillment capability. */
        boolean isDineInAvailable,
        /** The real-time, day-aware answer — computed server-side from the restaurant's actual weeklySchedule. Use this, not openingTime/closingTime, for any "is it open" / grey-out logic. */
        RestaurantOpenStatus openStatus,
        /** Straight-line distance from the request's lat/lng to this restaurant - null unless both were provided and this restaurant's own coordinates are valid. */
        BigDecimal distanceKm
) {
}
