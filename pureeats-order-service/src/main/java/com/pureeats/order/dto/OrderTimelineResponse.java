package com.pureeats.order.dto;

import java.time.LocalDateTime;

/**
 * Derived from {@code OrderStatusLog} (the same append-only journey trail, not a separately
 * maintained set of columns) - the first time each milestone status was reached, or null if the
 * order never got there.
 */
public record OrderTimelineResponse(
        LocalDateTime placedAt,
        LocalDateTime restaurantAcceptedAt,
        LocalDateTime restaurantReadyAt,
        LocalDateTime riderAssignedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime selfPickupCompletedAt,
        LocalDateTime cancelledAt
) {
}
