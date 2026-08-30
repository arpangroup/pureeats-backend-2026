package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String house,
        @NotBlank String address,
        String landmark,
        String tag,
        @NotBlank String latitude,
        @NotBlank String longitude,
        boolean makeDefault
) {
}
