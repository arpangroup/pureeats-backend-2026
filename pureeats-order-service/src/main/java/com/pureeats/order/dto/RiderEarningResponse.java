package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One delivered trip as an earning - what the rider was paid for it and why. {@code commissionRate}/
 * {@code commissionBasis}/{@code commissionBase} come from the snapshot taken at delivery time (the
 * rider's current rate for trips recorded before snapshots existed - see {@code rateIsCurrent}).
 */
public record RiderEarningResponse(
        Long tripId,
        Long orderId,
        String uniqueOrderId,
        String restaurantName,
        String customerAddress,
        LocalDateTime deliveredAt,
        BigDecimal distanceKm,
        BigDecimal orderTotal,
        BigDecimal deliveryCharge,
        String paymentMode,
        BigDecimal commissionRate,
        /** FULL_ORDER or DELIVERY_CHARGE_ONLY. */
        String commissionBasis,
        BigDecimal commissionBase,
        /** True when the rate/basis above is the rider's CURRENT one (old trip without a snapshot), not the one in force at delivery. */
        boolean rateIsCurrent,
        BigDecimal earning,
        /** Cash collected from the customer on a COD order - held by the rider until settlement. */
        BigDecimal codCollected,
        boolean settled,
        Long settlementId,
        LocalDateTime settledAt
) {
}
