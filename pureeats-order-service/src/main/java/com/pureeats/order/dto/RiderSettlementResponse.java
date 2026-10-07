package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A settlement between the platform and a rider - see the RiderSettlement entity for the netting rule. */
public record RiderSettlementResponse(
        Long id,
        Long riderUserId,
        BigDecimal earningsAmount,
        BigDecimal codAmount,
        BigDecimal netAmount,
        /** PAID_TO_RIDER, COLLECTED_FROM_RIDER or EVEN. */
        String direction,
        int tripCount,
        String transactionMode,
        String transactionReference,
        String note,
        Long settledBy,
        LocalDateTime createdAt,
        /** REQUESTED (withdrawal waiting for the admin), PAID or REJECTED. */
        String status,
        /** Set when the partner asked for it from the app (a withdrawal request). */
        LocalDateTime requestedAt,
        LocalDateTime paidAt,
        /** For the admin queue: who and where to pay. */
        String riderName,
        String payoutTo
) {
}
