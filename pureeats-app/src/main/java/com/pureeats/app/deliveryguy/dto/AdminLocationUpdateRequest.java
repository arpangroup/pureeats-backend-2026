package com.pureeats.app.deliveryguy.dto;

import jakarta.validation.constraints.NotBlank;

/** Admin override for {@code AdminDeliveryGuyController#setLocation} - {@code String}, not {@code double}, matching the same lat/lng convention as order-service's {@code GpsPingRequest}/{@code LocationPingRequest}. */
public record AdminLocationUpdateRequest(@NotBlank String lat, @NotBlank String lng) {
}
