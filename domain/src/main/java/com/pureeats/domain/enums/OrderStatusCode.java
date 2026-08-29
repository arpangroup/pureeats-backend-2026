package com.pureeats.domain.enums;

/**
 * Clean order state machine used by the API layer. Persisted indirectly via
 * {@code Order.orderstatusId}, which references a row in the legacy
 * {@code order_statuses} table seeded with these exact names.
 */
public enum OrderStatusCode {
    PLACED,
    RESTAURANT_ACCEPTED,
    READY_FOR_PICKUP,
    RIDER_ASSIGNED,
    PICKED_UP,
    DELIVERED,
    SELF_PICKUP_COMPLETED,
    CANCELLED
}
