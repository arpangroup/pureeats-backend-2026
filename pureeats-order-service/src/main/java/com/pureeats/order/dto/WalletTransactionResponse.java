package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(Long id, String type, BigDecimal amount, String meta, LocalDateTime createdAt) {
}
