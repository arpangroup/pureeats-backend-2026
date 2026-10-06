package com.pureeats.order.dto;

import java.time.LocalDateTime;

/**
 * GET /api/v1/delivery/status - the rider's server-side availability. {@code offlineReason} is one of
 * DeliveryGuyDetail.OFFLINE_REASON_* (SELF / INACTIVITY / ADMIN), null while online.
 */
public record RiderStatusResponse(
        boolean isOnline,
        String offlineReason,
        LocalDateTime statusChangedAt,
        LocalDateTime lastSeenAt
) {
}
