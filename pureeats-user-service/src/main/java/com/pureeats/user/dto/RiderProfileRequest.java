package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RiderProfileRequest(
        @NotBlank String vehicleNumber,
        String age,
        String gender,
        String photo,
        String description
) {
}
