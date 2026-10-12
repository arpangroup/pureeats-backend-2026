package com.pureeats.order.dto;

/** {@code address} and {@code licenceNumber} (Restaurant.certificate, e.g. FSSAI) are for the invoice; either may be null. */
public record OrderRestaurantSummary(Long id, String name, String contactNumber, String image, String address, String licenceNumber) {
}
