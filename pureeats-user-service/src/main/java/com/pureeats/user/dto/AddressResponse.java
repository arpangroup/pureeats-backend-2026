package com.pureeats.user.dto;

public record AddressResponse(
        Long id,
        String house,
        String address,
        String landmark,
        String tag,
        String latitude,
        String longitude,
        boolean isDefault
) {
}
