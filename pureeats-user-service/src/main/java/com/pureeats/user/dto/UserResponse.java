package com.pureeats.user.dto;

import com.pureeats.domain.enums.Role;

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        String photo,
        Role role,
        Long defaultAddressId
) {
}
