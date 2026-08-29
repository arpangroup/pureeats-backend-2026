package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;

public record SupportRequest(Long orderId, Long restaurantId, @NotBlank String issue, String message) {
}
