package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One order assigned to the calling rider - whether they accepted it themself or an admin assigned
 * it to them (see {@code DeliveryOrderService#assignDriverAsAdmin}). Backs GET
 * /api/v1/delivery/orders/active and /orders/mine, and carries everything the rider app's active
 * delivery screen needs in one round-trip. {@code status} is the raw {@code OrderStatusCode} name
 * (e.g. {@code RIDER_ASSIGNED}), not its display label - the app branches on it.
 * <p>
 * Deliberately has no delivery PIN - the customer reads that out at the door.
 */
public record DeliveryAssignmentResponse(
        Long id,
        String uniqueOrderId,
        String status,
        /** "ADMIN" when ops assigned this order to the rider, "DELIVERY" when the rider accepted it themself. */
        String assignedBy,
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        BigDecimal restaurantLat,
        BigDecimal restaurantLng,
        String restaurantContactNumber,
        String customerName,
        String customerAddress,
        BigDecimal customerLat,
        BigDecimal customerLng,
        String customerPhone,
        List<Item> items,
        BigDecimal total,
        String paymentMode,
        /** Commission only - the tip is separate, see {@code tipAmount}. */
        BigDecimal payoutEstimate,
        BigDecimal distanceKm,
        /** Customer's tip - paid to the rider in full on delivery. */
        BigDecimal tipAmount,
        LocalDateTime createdAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt
) {
    public record Item(String name, int quantity) {
    }
}
