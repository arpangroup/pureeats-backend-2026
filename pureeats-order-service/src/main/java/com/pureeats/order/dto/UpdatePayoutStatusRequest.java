package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePayoutStatusRequest(
        /** "pending" | "processing" | "paid" | "rejected". */
        @NotBlank String status
) {
}
