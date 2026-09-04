package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record RestaurantCreateRequest(
        @NotBlank String name,
        String description,
        @NotBlank String contactNumber,
        @NotNull LocalTime openingTime,
        @NotNull LocalTime closingTime,
        String image,
        @NotBlank String address,
        String pincode,
        String landmark,
        @NotBlank String latitude,
        @NotBlank String longitude,
        @NotNull Boolean isPureveg,
        @NotNull BigDecimal deliveryCharges,
        @NotNull BigDecimal deliveryRadius,
        @NotNull BigDecimal minOrderPrice,
        boolean isAcceptCod,
        /** Optional - omit to leave every day unset (closed) until edited later via patch. */
        List<DayScheduleDto> weeklySchedule,
        /** Optional - cuisine category ids (see {@code RestaurantCategory}) this restaurant belongs to. */
        List<Long> categoryIds,
        /** Optional - estimated prep+delivery time in minutes. */
        Integer deliveryTime,
        /**
         * Optional, ADMIN/SUPER_ADMIN only - ignored on store-owner self-onboarding (see
         * {@code RestaurantService#create}) so a submitted value can never let an owner set their
         * own commission rate; defaults to 10% when omitted or ignored.
         */
        BigDecimal commissionRate
) {
}
