package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record RestaurantDetailResponse(
        Long id,
        String name,
        String description,
        String slug,
        String contactNumber,
        LocalTime openingTime,
        LocalTime closingTime,
        String image,
        String rating,
        String deliveryTime,
        String priceRange,
        boolean isPureveg,
        String address,
        String pincode,
        String landmark,
        String certificate,
        String locationId,
        String latitude,
        String longitude,
        BigDecimal restaurantCharges,
        BigDecimal deliveryCharges,
        BigDecimal deliveryRadius,
        BigDecimal minOrderPrice,
        /** "self-pickup" | "delivery" | "both" - mapped from the legacy 0/1/2 column. */
        String deliveryType,
        String deliveryChargeType,
        BigDecimal baseDeliveryCharge,
        Integer baseDeliveryDistance,
        BigDecimal extraDeliveryCharge,
        Integer extraDeliveryDistance,
        boolean isSchedulable,
        boolean isNotifiable,
        boolean isActive,
        boolean isAccepted,
        boolean isFeatured,
        boolean isAcceptCod,
        boolean autoAcceptable,
        List<DayScheduleDto> weeklySchedule
) {
}
