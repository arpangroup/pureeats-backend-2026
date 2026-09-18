package com.pureeats.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Plain JSON body - used for both onboarding (POST) and editing (PUT) a rider's own profile.
 * Deliberately carries no photo field: a photo is a file, not JSON, and goes through the separate
 * {@code POST /api/v1/users/me/rider-profile/photo} multipart endpoint instead, mirroring how the
 * customer app's own profile photo upload is already a separate action from saving text fields.
 */
public record RiderProfileRequest(
        String name,
        @NotBlank String vehicleNumber,
        String age,
        String gender,
        String description
) {
}
