package com.pureeats.user.dto;

import com.pureeats.domain.enums.Role;

import java.time.LocalDateTime;

/** Admin-panel user row/detail - unlike {@link UserResponse} (self-profile), this includes {@code isActive} and audit timestamps. */
public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        String photo,
        Role role,
        boolean isActive,
        Integer defaultAddressId,
        Integer deliveryGuyDetailId,
        String deliveryPin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
