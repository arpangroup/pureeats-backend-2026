package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Every field optional - only fields actually present get applied and diffed for the audit log.
 * {@code name}, {@code commissionRate}, {@code isActive}, {@code isAccepted}, {@code autoAcceptable},
 * {@code isFeatured} are admin/super-admin only (see {@code RestaurantService.ADMIN_ONLY_FIELDS}).
 * <p>
 * {@code weeklySchedule} is included even though it's a JSON blob under the hood
 * ({@code RestaurantScheduleCodec} validates and (de)serializes it against
 * {@code Restaurant.scheduleData}), and {@code categoryIds} even though it's backed by the
 * {@code restaurant_category_restaurant} join table rather than a column - both still fit the
 * "whole form, one save" flow the admin edit page uses, they just don't go through the generic
 * {@code applyField} scalar diff.
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
        /** Estimated prep+delivery time in minutes. */
        Integer deliveryTime,
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
        List<DayScheduleDto> weeklySchedule,
        List<Long> categoryIds
) {
}
