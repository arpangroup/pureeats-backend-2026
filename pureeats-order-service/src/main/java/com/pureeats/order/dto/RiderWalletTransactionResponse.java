package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A rider wallet ledger entry, linked to what caused it so the app can open its details:
 * EARNING (a delivery - {@code orderId} set), SETTLEMENT ({@code settlementId} set) or ADJUSTMENT
 * (a manual admin credit/debit).
 */
public record RiderWalletTransactionResponse(
        Long id,
        /** credit or debit. */
        String type,
        BigDecimal amount,
        String note,
        LocalDateTime createdAt,
        String kind,
        Long orderId,
        String uniqueOrderId,
        Long settlementId
) {
}
