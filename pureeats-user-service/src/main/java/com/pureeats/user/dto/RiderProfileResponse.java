package com.pureeats.user.dto;

import java.math.BigDecimal;

/**
 * The rider's own profile - combines their {@code User} identity (name/email/phone/photo) with
 * their {@code DeliveryGuyDetail} rider-specific fields. Previously only carried the
 * DeliveryGuyDetail half, which silently meant the delivery app's profile page never had a name,
 * photo, email, phone, age, gender, description, or online/active status to render - it just
 * rendered a mostly-empty profile with no backend error to explain why.
 */
public record RiderProfileResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String phone,
        String photo,
        String vehicleNumber,
        String age,
        String gender,
        String description,
        BigDecimal commissionRate,
        Integer maxAcceptDeliveryLimit,
        BigDecimal rating,
        boolean isNotifiable,
        boolean isOnline,
        boolean isActive
) {
}
