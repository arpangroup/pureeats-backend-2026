package com.pureeats.catalog.dto;

/** Shared bean-validation constants for restaurant request DTOs. */
final class RestaurantValidation {

    /** A generous upper bound for any real food-delivery radius - large enough for wide-area/rural service, small enough to catch an accidental typo (e.g. "700" or "5011" meant to be "7"/"5") before it silently lets orders hundreds of km away through as "in range". */
    static final String MAX_DELIVERY_RADIUS_KM = "50";

    private RestaurantValidation() {
    }
}
