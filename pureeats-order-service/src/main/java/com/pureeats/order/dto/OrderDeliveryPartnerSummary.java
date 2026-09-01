package com.pureeats.order.dto;

/** Null until a rider has been assigned (via the delivery flow or admin override). */
public record OrderDeliveryPartnerSummary(Long id, String name, String phone, String photo, String vehicleNumber) {
}
