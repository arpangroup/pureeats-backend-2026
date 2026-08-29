package com.pureeats.user.service;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.dto.UserResponse;

final class UserMapper {

    private UserMapper() {
    }

    static UserResponse toResponse(User user, Role role) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getPhoto(),
                role,
                user.getDefaultAddressId() != null ? user.getDefaultAddressId().longValue() : null);
    }
}
