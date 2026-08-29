package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GpsPingRequest(
        @NotNull Long orderId,
        @NotBlank String deliveryLat,
        @NotBlank String deliveryLong,
        String heading,
        String bearing
) {
}
