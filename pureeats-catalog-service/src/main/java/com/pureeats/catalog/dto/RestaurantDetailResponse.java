package com.pureeats.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

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
        String latitude,
        String longitude,
        BigDecimal restaurantCharges,
        BigDecimal deliveryCharges,
        BigDecimal deliveryRadius,
        BigDecimal minOrderPrice,
        boolean isActive,
        boolean isAccepted,
        boolean isFeatured,
        boolean isAcceptCod,
        boolean autoAcceptable
) {
}
