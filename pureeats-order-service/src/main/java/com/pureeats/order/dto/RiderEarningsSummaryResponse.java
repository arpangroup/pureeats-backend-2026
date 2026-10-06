package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * Where the rider stands right now. {@code pendingEarnings} = commission on trips not yet settled;
 * {@code cashInHand} = COD cash collected on those trips (owed to the platform);
 * {@code netPending = pendingEarnings - cashInHand} - positive means the platform owes the rider,
 * negative means the rider owes the platform.
 */
public record RiderEarningsSummaryResponse(
        BigDecimal lifetimeEarnings,
        int lifetimeTrips,
        BigDecimal pendingEarnings,
        BigDecimal cashInHand,
        BigDecimal netPending,
        /** PAID_TO_RIDER (platform owes rider), COLLECTED_FROM_RIDER (rider owes platform) or EVEN. */
        String netDirection,
        int unsettledTrips,
        BigDecimal settledEarnings,
        RiderSettlementResponse lastSettlement
) {
}
