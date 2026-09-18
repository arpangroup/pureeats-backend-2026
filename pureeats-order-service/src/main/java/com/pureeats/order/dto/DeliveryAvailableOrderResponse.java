package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What a rider actually needs to decide whether to accept a pickup - unlike {@link OrderSummaryResponse}
 * (shared with the customer's own order list, where a rider's payout has no business being exposed),
 * this is dedicated to {@code GET /api/v1/delivery/orders/available} and is deliberately rider-specific:
 * {@code payoutEstimate} is computed against the CALLING rider's own commission rate (mirrors
 * {@code DeliveryOrderService#creditRiderAndSettle}'s math exactly, so what's shown here matches what
 * they'd actually be credited), and {@code distanceKm}/the lat-lng pairs exist so the app can render a
 * pickup-to-drop route without a follow-up restaurant-detail fetch.
 */
public record DeliveryAvailableOrderResponse(
        Long id,
        String uniqueOrderId,
        String restaurantName,
        String restaurantAddress,
        BigDecimal restaurantLat,
        BigDecimal restaurantLng,
        String customerAddress,
        BigDecimal customerLat,
        BigDecimal customerLng,
        BigDecimal distanceKm,
        BigDecimal payoutEstimate,
        int itemsCount,
        LocalDateTime createdAt
) {
}
