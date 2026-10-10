package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String uniqueOrderId,
        String status,
        Long restaurantId,
        String restaurantName,
        String restaurantImage,
        BigDecimal total,
        LocalDateTime createdAt,
        /** Null until a rider has been assigned. */
        String deliveryGuyName,
        /** The customer's note for the order (e.g. "no onions", "ring the bell"), or null. */
        String orderComment,
        /** T1 - preparation minutes. */
        Integer prepTimeMinutes,
        /** When the food should be ready (acceptance + T1) - the kitchen's countdown target. */
        java.time.LocalDateTime prepDueAt,
        /** Base ETA = T1 + T2 + T3 from createdAt (null for older orders). */
        Integer etaMinutes,
        /** The customer's countdown runs this many times slower than real time. */
        Double etaSlowdownFactor
) {
}
