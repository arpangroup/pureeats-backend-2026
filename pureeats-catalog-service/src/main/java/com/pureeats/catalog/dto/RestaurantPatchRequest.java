package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Every field optional - only fields actually present get applied and diffed for the audit log.
 * {@code name}, {@code commissionRate}, {@code isActive}, {@code isAccepted}, {@code autoAcceptable},
 * {@code isFeatured} are admin/super-admin only (see {@code RestaurantService.ADMIN_ONLY_FIELDS}).
 * <p>
 * Deliberately excludes {@code categoryIds} (a join-table, not a scalar field) - that doesn't fit
 * this generic diff-and-apply pattern and needs its own endpoint. {@code weeklySchedule} is
 * included even though it's a JSON blob under the hood ({@code RestaurantScheduleCodec} validates
 * and (de)serializes it against {@code Restaurant.scheduleData}) - the admin form always saves the
 * whole week in the same request as every other field, so it belongs in the same patch.
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
        BigDecimal commissionRate,
        List<DayScheduleDto> weeklySchedule
) {
}
