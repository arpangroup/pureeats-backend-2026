package com.pureeats.user.dto;

import com.pureeats.domain.enums.Gender;
import com.pureeats.domain.enums.Role;

import java.time.LocalDate;

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        String photo,
        Role role,
        Long defaultAddressId,
        LocalDate dob,
        Gender gender
) {
}
