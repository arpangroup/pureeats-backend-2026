package com.pureeats.user.dto;

import com.pureeats.domain.enums.Role;

import java.time.LocalDateTime;

/** Admin-panel user row/detail - unlike {@link UserResponse} (self-profile), this includes {@code isActive}, {@code accountStatus} and audit timestamps. {@code accountStatus} is always a concrete value here (never null) - a null column value (rows predating the column) is normalized to "ACTIVE" before this is built, matching how every other status check in this codebase already treats it. */
public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        String photo,
        Role role,
        boolean isActive,
        String accountStatus,
        Integer defaultAddressId,
        Integer deliveryGuyDetailId,
        String deliveryPin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
