package com.pureeats.order.dto;

import jakarta.validation.constraints.Size;

/** Admin input for settling everything a rider currently has unsettled. */
public record SettleRiderRequest(
        /** e.g. BANK_TRANSFER, UPI, CASH. */
        @Size(max = 32) String transactionMode,
        @Size(max = 128) String transactionReference,
        @Size(max = 500) String note
) {
}
