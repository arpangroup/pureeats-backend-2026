package com.pureeats.order.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Rider self-service, principal-scoped location ping - deliberately separate from {@link
 * GpsPingRequest} (which is order-scoped and requires an {@code orderId} for the in-transit
 * tracking shown to a customer on one specific order). This one just keeps the rider's own {@code
 * DeliveryGuyDetail.lastLat}/{@code lastLng}/{@code lastSeenAt} fresh for "who's online and nearby"
 * purposes (available-orders sorting, admin dashboard, etc.), independent of any single order.
 * {@code String}, not {@code double}, to match {@code GpsPingRequest}'s existing lat/lng convention.
 */
public record LocationPingRequest(@NotBlank String lat, @NotBlank String lng) {
}
