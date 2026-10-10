package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Live travel time to the customer. {@code from}: RIDER (partner's last position) or RESTAURANT (before pickup). */
public record DeliveryEtaResponse(int minutes, BigDecimal distanceKm, String from, LocalDateTime computedAt) {
}
