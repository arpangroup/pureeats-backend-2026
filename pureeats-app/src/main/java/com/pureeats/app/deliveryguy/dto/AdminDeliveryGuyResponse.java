package com.pureeats.app.deliveryguy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminDeliveryGuyResponse(
        Long id,
        Long userId,
        String name,
        Integer age,
        String gender,
        String photo,
        String description,
        String vehicleNumber,
        BigDecimal commissionRate,
        boolean isNotifiable,
        Integer maxAcceptDeliveryLimit,
        BigDecimal rating,
        boolean isActive,
        boolean isOnline,
        BigDecimal lastLat,
        BigDecimal lastLng,
        LocalDateTime lastSeenAt,
        /** Why the rider is offline: SELF, INACTIVITY (auto-offlined by RiderInactivityScheduler - shown as "Forced stop") or ADMIN; null while online. */
        String offlineReason,
        LocalDateTime statusChangedAt,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String email,
        String phone,
        boolean isUserActive
) {
}
