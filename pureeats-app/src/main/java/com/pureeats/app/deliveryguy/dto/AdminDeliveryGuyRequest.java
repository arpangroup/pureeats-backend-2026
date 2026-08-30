package com.pureeats.app.deliveryguy.dto;

import java.math.BigDecimal;

/** Used for both create and update - {@code email} is ignored (and may be omitted) on update, since a rider's login email isn't editable here. */
public record AdminDeliveryGuyRequest(
        String name,
        String email,
        Integer age,
        String gender,
        String vehicleNumber,
        String description,
        BigDecimal commissionRate,
        Integer maxAcceptDeliveryLimit,
        Boolean isNotifiable,
        Boolean isActive,
        Boolean isOnline,
        BigDecimal rating,
        String photo
) {
}
