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
        /** Restaurant -> customer (same as {@code dropDistanceKm}; kept for older app builds). */
        BigDecimal distanceKm,
        /** Commission only - the tip is separate, see {@code tipAmount}. */
        BigDecimal payoutEstimate,
        int itemsCount,
        LocalDateTime createdAt,
        /** Customer's tip for the rider - paid to the rider in full on delivery, on top of {@code payoutEstimate}. */
        BigDecimal tipAmount,
        /** Rider's last reported position -> restaurant; null when the rider app hasn't reported a position yet. */
        BigDecimal pickupDistanceKm,
        /** Restaurant -> customer. */
        BigDecimal dropDistanceKm,
        /** Kitchen status: RESTAURANT_ACCEPTED, PREPARING or READY_FOR_PICKUP. */
        String orderStatus,
        /** COD, RAZORPAY, WALLET... - the app shows COD vs Prepaid. */
        String paymentMode,
        /** When the food should be ready (acceptance + prep time) - drives the pickup countdown. */
        LocalDateTime pickupDueAt
) {
}
