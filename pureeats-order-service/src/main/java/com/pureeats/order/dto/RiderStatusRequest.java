package com.pureeats.order.dto;

import jakarta.validation.constraints.NotNull;

/** Rider self-service online/offline toggle - see {@code DeliveryOrderController#setStatus}. */
public record RiderStatusRequest(@NotNull Boolean isOnline) {
}
