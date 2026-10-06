package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * The restaurant's delivery rates that produced a delivery charge, snapshotted at order time (inside
 * {@link PricingBreakdown}) so the admin panel can show exactly how the charge was calculated even
 * if the restaurant changes its rates later.
 * <p>
 * FIXED: {@code flatCharge}. DYNAMIC: {@code baseCharge} covers the first {@code baseDistanceKm};
 * beyond that, every started {@code extraDistanceKm} step adds {@code extraCharge}:
 * {@code baseCharge + ceil((distance - baseDistanceKm) / extraDistanceKm) * extraCharge}.
 * {@code extraUnits} is that ceil(...) value as actually applied.
 */
public record DeliveryChargeRates(
        BigDecimal flatCharge,
        BigDecimal baseCharge,
        Integer baseDistanceKm,
        BigDecimal extraCharge,
        Integer extraDistanceKm,
        Integer extraUnits
) {
}
