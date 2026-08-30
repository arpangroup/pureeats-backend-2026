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
        LocalDateTime updatedAt
) {
}
