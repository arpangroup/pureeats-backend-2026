package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record AdminWalletTransactionResponse(
        Long id,
        String payableType,
        Long payableId,
        Long walletId,
        /** "credit" or "debit" - mapped from the ledger's internal deposit/withdraw types. */
        String type,
        BigDecimal amount,
        boolean confirmed,
        Map<String, Object> meta,
        String uuid,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
