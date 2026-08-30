package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Every field optional - only fields actually present get applied and diffed for the audit log.
 * {@code name}, {@code commissionRate}, {@code isActive}, {@code isAccepted}, {@code autoAcceptable},
 * {@code isFeatured} are admin/super-admin only (see {@code RestaurantService.ADMIN_ONLY_FIELDS}).
 */
public record RestaurantPatchRequest(
        String name,
        String description,
        String contactNumber,
        LocalTime openingTime,
        LocalTime closingTime,
        String address,
        String pincode,
        String landmark,
        BigDecimal deliveryCharges,
        BigDecimal deliveryRadius,
        BigDecimal minOrderPrice,
        Boolean isAcceptCod,
        Boolean autoAcceptable,
        Boolean isActive,
        Boolean isAccepted,
        Boolean isFeatured,
        BigDecimal commissionRate
) {
}
