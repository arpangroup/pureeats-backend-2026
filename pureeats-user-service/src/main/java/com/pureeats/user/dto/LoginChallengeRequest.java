package com.pureeats.user.dto;

import com.pureeats.user.enums.AuthenticationMethod;
import jakarta.validation.constraints.NotNull;

/**
 * {@code method} decides which of {@code countryId+phone} or {@code email} is actually required -
 * enforced in {@code AuthenticationService}, not via Bean Validation groups, since the two shapes
 * share no fields worth a cross-field constraint annotation.
 */
public record LoginChallengeRequest(
        @NotNull(message = "method is required") AuthenticationMethod method,
        Integer countryId,
        String phone,
        String email
) {
}
