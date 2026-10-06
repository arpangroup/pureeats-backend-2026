package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/orders/{id}/tracking - everything the customer's live tracking map needs in one poll:
 * the restaurant and the order's own delivery point (not whatever address the customer has selected
 * in the app now), plus - while a rider is on the order - their latest GPS position and the path
 * they've driven since being assigned. {@code status} is the raw OrderStatusCode name.
 */
public record OrderTrackingResponse(
        Long orderId,
        String status,
        Point restaurant,
        Point destination,
        /** Null until a rider is assigned, after the order is finished, or before the rider's app has reported any position. */
        RiderPosition rider,
        /** Rider breadcrumbs for this order, oldest first (empty until the rider app reports a position). */
        List<Point> path
) {
    public record Point(BigDecimal lat, BigDecimal lng) {
    }

    public record RiderPosition(BigDecimal lat, BigDecimal lng, LocalDateTime updatedAt,
                                /** True when the last fix is older than a couple of minutes (app backgrounded / no signal). */
                                boolean stale) {
    }
}
