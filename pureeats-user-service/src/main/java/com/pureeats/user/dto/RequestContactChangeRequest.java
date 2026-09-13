package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for both {@code POST /users/me/phone/otp} (destination = new phone number) and {@code POST /users/me/email/otp} (destination = new email address). */
public record RequestContactChangeRequest(
        @NotBlank String destination
) {
}
