package com.pureeats.user.dto;

import com.pureeats.domain.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record UpdateUserRequest(
        @NotBlank String name,
        String photo,
        @Past LocalDate dob,
        Gender gender
) {
}
