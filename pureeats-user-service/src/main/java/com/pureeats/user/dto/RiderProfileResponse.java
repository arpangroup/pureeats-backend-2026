package com.pureeats.user.dto;

import java.math.BigDecimal;

public record RiderProfileResponse(
        Long id,
        String vehicleNumber,
        BigDecimal commissionRate,
        Integer maxAcceptDeliveryLimit,
        BigDecimal rating,
        boolean isNotifiable
) {
}
