package com.pureeats.domain.enums;

import com.pureeats.domain.common.exception.BadRequestException;

/**
 * Every state an order can be in. {@link #label()} is the human-friendly display string the
 * admin panel's badges/dropdowns and its client-side order-journey diagram key off of (see
 * pureeats-admin-react-app-2026's {@code orderJourney.ts}) - keep these exact strings in sync
 * with that file's rank/status tables if either side changes.
 */
public enum OrderStatusCode {
    PLACED("Placed"),
    RESTAURANT_ACCEPTED("Accepted"),
    PREPARING("Preparing"),
    READY_FOR_PICKUP("Ready for Pickup"),
    /** Logged as an event / shown as the order's current status, but not a hard requirement to reach PICKED_UP - see {@link com.pureeats.domain.entity.Order}. */
    RIDER_ASSIGNED("Rider Assigned"),
    PICKED_UP("Picked Up"),
    ON_THE_WAY("On the way"),
    DELIVERED("Delivered"),
    /** Distinct from {@link #DELIVERED} only in label - same terminal meaning, reached by a self-pickup order instead of a rider-delivered one. */
    SELF_PICKUP_COMPLETED("Delivered (Self-Pickup)"),
    /** Customer-initiated cancellation - see {@link #REJECTED} for the restaurant-initiated equivalent and {@link #AUTO_CANCELLED} for the system-initiated one. */
    CANCELLED("Cancelled"),
    /** Restaurant declines the order outright, before ever accepting it. */
    REJECTED("Rejected"),
    /** A post-pickup failure (customer unreachable, quality issue, ...) - cancelling no longer means what it does earlier in the flow once a rider has the food. */
    RETURNED("Returned"),
    /** System-initiated: no restaurant response, or no rider found, within the allowed window. */
    AUTO_CANCELLED("Auto-Cancelled");

    private final String label;

    OrderStatusCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Resolves either the raw enum constant name (e.g. {@code "RESTAURANT_ACCEPTED"}) or its
     * {@link #label()} (e.g. {@code "Accepted"}) - the admin panel's status dropdown round-trips
     * whatever {@link #label()} produced, so both forms must keep working.
     */
    public static OrderStatusCode fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Order status is required");
        }
        for (OrderStatusCode code : values()) {
            if (code.name().equalsIgnoreCase(value) || code.label.equalsIgnoreCase(value)) {
                return code;
            }
        }
        throw new BadRequestException("Unknown order status: " + value);
    }
}
