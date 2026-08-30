package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Every field optional - only fields actually present get applied and diffed for the audit log.
 * {@code name}, {@code commissionRate}, {@code isActive}, {@code isAccepted}, {@code autoAcceptable},
 * {@code isFeatured} are admin/super-admin only (see {@code RestaurantService.ADMIN_ONLY_FIELDS}).
 * <p>
 * Deliberately excludes {@code weeklySchedule} (its own JSON-blob serialization) and
 * {@code categoryIds} (a join-table, not a scalar field) - neither fits this generic
 * diff-and-apply pattern and both need dedicated endpoints.
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
        String certificate,
        Boolean isPureveg,
        String locationId,
        String latitude,
        String longitude,
        BigDecimal restaurantCharges,
        BigDecimal deliveryCharges,
        BigDecimal deliveryRadius,
        BigDecimal minOrderPrice,
        /** "self-pickup" | "delivery" | "both" - mapped to the legacy 0/1/2 column internally. */
        String deliveryType,
        String deliveryChargeType,
        BigDecimal baseDeliveryCharge,
        Integer baseDeliveryDistance,
        BigDecimal extraDeliveryCharge,
        Integer extraDeliveryDistance,
        Boolean isSchedulable,
        Boolean isNotifiable,
        Boolean isAcceptCod,
        Boolean autoAcceptable,
        Boolean isActive,
        Boolean isAccepted,
        Boolean isFeatured,
        BigDecimal commissionRate
) {
}
