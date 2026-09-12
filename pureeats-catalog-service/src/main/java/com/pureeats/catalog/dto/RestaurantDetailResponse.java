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
        Integer deliveryTime,
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
        /** Purely informational - whether this restaurant also seats walk-in/dine-in customers. Independent of {@code deliveryType} (self-pickup/delivery/both), which governs actual order fulfillment; dine-in never goes through the cart/checkout pipeline. */
        boolean isDineInAvailable,
        BigDecimal commissionRate,
        Integer offerDiscountPercent,
        BigDecimal offerMaxDiscount,
        List<DayScheduleDto> weeklySchedule,
        List<Long> categoryIds,
        /** The real-time, day-aware open/closed answer — computed server-side from weeklySchedule. openingTime/closingTime above are legacy and not day-aware; use this instead. */
        RestaurantOpenStatus openStatus
) {
}
