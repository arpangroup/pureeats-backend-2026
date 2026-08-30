package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record AdminTransactionResponse(
        Long id,
        String payableType,
        Long payableId,
        Long walletId,
        String type,
        BigDecimal amount,
        boolean confirmed,
        Map<String, Object> meta,
        String uuid,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String walletName,
        String walletHolderType,
        Long walletHolderId
) {
}
