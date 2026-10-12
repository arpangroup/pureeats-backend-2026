package com.pureeats.app.deliveryguy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TripDetailResponse(
        Long id,
        Long orderId,
        Long customerId,
        Long restaurantId,
        Long riderId,
        Long deliveryCollectionId,
        BigDecimal distanceTravelled,
        BigDecimal riderEarning,
        BigDecimal restaurantEarning,
        BigDecimal cashCollectedFromCustomer,
        BigDecimal cashOnHold,
        boolean isSettlementDone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** The order's number as everyone sees it (e.g. PE-1A2B3C4D) - orderId is the internal id the order page uses. */
        String uniqueOrderId
) {
}
