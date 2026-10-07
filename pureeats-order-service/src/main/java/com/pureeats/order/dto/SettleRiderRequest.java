package com.pureeats.order.dto;

import jakarta.validation.constraints.Size;

/** Admin input for settling everything a rider currently has unsettled. */
public record SettleRiderRequest(
        /** e.g. BANK_TRANSFER, UPI, CASH. */
        @Size(max = 32) String transactionMode,
        @Size(max = 128) String transactionReference,
        @Size(max = 500) String note,
        /** Collect all the COD cash the rider holds (default true). */
        Boolean collectCod,
        /** Pay out all pending earnings (default true). */
        Boolean payEarnings
) {
    public boolean collectsCod() {
        return collectCod == null || collectCod;
    }

    public boolean paysEarnings() {
        return payEarnings == null || payEarnings;
    }
}
