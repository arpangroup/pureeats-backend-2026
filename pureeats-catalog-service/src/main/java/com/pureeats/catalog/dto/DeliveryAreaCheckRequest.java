package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliveryAreaCheckRequest(@NotBlank String latitude, @NotBlank String longitude) {
}
