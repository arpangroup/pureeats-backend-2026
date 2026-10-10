package com.pureeats.user.dto;


/**
 * Plain JSON body - used for both onboarding (POST) and editing (PUT) a rider's own profile.
 * Deliberately carries no photo field: a photo is a file, not JSON, and goes through the separate
 * {@code POST /api/v1/users/me/rider-profile/photo} multipart endpoint instead, mirroring how the
 * customer app's own profile photo upload is already a separate action from saving text fields.
 */
public record RiderProfileRequest(
        String name,
        /** Required for motor vehicles - checked by RiderKyc (a cycle has none). */
        String vehicleNumber,
        String age,
        String gender,
        String description,
        // Sign-up details (required when applying; editable again only while pending/rejected).
        String licenseNumber,
        /** Legacy single ID proof (AADHAAR or PAN) from older app versions - see aadhaarNumber / panNumber. */
        String idProofType,
        String idProofNumber,
        /** Both are required on an application. */
        String aadhaarNumber,
        String panNumber,
        /** BIKE, CYCLE or EV. */
        String vehicleType,
        /** BANK or UPI. */
        String payoutMethod,
        String bankAccountHolder,
        String bankAccountNumber,
        String bankIfsc,
        String upiId
) {
    /** Older app versions send only the basic profile fields. */
    public boolean hasKyc() {
        return licenseNumber != null || idProofNumber != null || aadhaarNumber != null || panNumber != null
                || vehicleType != null || payoutMethod != null;
    }
}
