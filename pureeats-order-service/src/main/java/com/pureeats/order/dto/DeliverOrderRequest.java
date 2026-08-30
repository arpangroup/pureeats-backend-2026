package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliverOrderRequest(@NotBlank String deliveryPin) {
}
