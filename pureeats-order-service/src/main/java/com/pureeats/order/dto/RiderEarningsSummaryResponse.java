package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * Where the rider stands right now. Two separate balances - they are never netted:
 * {@code cashInHand} = COD cash collected on delivered orders and not yet handed over (the rider pays ALL of it
 * to the platform); {@code pendingEarnings} = commission + tips not yet paid out (the platform pays all of it to the rider).
 * {@code netPending}/{@code netDirection} are kept only for older app versions.
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
        RiderSettlementResponse lastSettlement,
        /** Delivered orders not fully settled yet (earning unpaid or COD cash still held), and their order value. */
        int openOrders,
        BigDecimal openOrderValue,
        /** COD orders whose cash is still with the rider. */
        int codOrders,
        /** Unpaid trips whose earning was recorded on a different basis than today's setting (e.g. on the order total). */
        int earningsOnOldBasis,
        /** Earnings in the wallet (credited at delivery, not yet paid out) - pendingEarnings carries the same value. */
        BigDecimal walletBalance,
        /** Withdrawal requests waiting for the admin - reserved from the balance. */
        BigDecimal pendingWithdrawals,
        /** What can be withdrawn or paid out now: walletBalance - pendingWithdrawals. */
        BigDecimal availableToWithdraw,
        /** Where earnings are paid (e.g. "UPI ravi@okhdfcbank"), null when the partner hasn't added one. */
        String payoutTo
) {
}
