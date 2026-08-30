package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WalletAdjustRequest(
        /** "credit" or "debit". */
        @NotBlank String type,
        @NotNull @Positive BigDecimal amount,
        String message
) {
}
