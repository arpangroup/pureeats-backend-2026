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
        boolean isUserActive,
        /** PENDING, APPROVED or REJECTED (legacy rows report APPROVED). */
        String approvalStatus,
        String rejectionReason,
        LocalDateTime approvalUpdatedAt,
        // Sign-up details for verification - shown in full to admins.
        String licenseNumber,
        String licensePhotoUrl,
        String idProofType,
        String idProofNumber,
        String vehicleType,
        String payoutMethod,
        String bankAccountHolder,
        String bankAccountNumber,
        String bankIfsc,
        String upiId,
        boolean phoneVerified
) {
}
