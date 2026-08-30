package com.pureeats.order.dto;

import java.math.BigDecimal;

public record DeliveryChargeResult(BigDecimal amount, BigDecimal distanceKm, String basis) {
}
