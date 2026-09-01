package com.pureeats.order.dto;

import java.math.BigDecimal;

public record DeliveryQuoteResponse(BigDecimal deliveryCharge, BigDecimal distanceKm, String basis) {
}
