package com.pureeats.order.service;

import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.dto.DeliveryChargeRates;
import com.pureeats.order.dto.DeliveryChargeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centralizes the tax/restaurant-charge/delivery-charge math that Laravel had copy-pasted per-controller. */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderPricingService {

    private final DistanceCalculator distanceCalculator;
    private final AppConfigService appConfigService;
    private final SettingValueService settingValueService;

    /** Fallback only - used until an admin saves Settings -> General -> Commerce -> Tax on orders. */
    @Value("${pureeats.tax.percentage:5}")
    private BigDecimal taxPercentage;

    public BigDecimal tax(BigDecimal amount) {
        return percentOf(amount, taxPercentage());
    }

    /** Admin-configurable order tax rate (setting {@link SettingSchemaService#TAX_PERCENTAGE}), falling back to {@code pureeats.tax.percentage}. */
    public BigDecimal taxPercentage() {
        String configured = settingValueService.getString(SettingSchemaService.TAX_PERCENTAGE, null);
        if (configured != null) {
            try {
                BigDecimal value = new BigDecimal(configured);
                if (value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0) return value;
                log.warn("Ignoring out-of-range tax setting '{}' - using fallback {}", configured, taxPercentage);
            } catch (NumberFormatException e) {
                log.warn("Ignoring non-numeric tax setting '{}' - using fallback {}", configured, taxPercentage);
            }
        }
        return taxPercentage;
    }

    /** Flat, admin-configurable (Settings → App config) — 0 until an admin sets one. */
    public BigDecimal platformFee() {
        return appConfigService.getPlatformFee();
    }

    public BigDecimal restaurantCharge(Restaurant restaurant, BigDecimal amount) {
        return percentOf(amount, restaurant.getRestaurantCharges());
    }

    /**
     * Distance-aware delivery charge: "dynamic" restaurants charge a base amount up to a base
     * distance, then an extra increment per extra-distance step beyond it; "fixed" restaurants
     * (the default) just charge their flat {@code deliveryCharges} regardless of distance. Distance
     * is still computed and returned either way, for the pricing-breakdown snapshot.
     */
    public DeliveryChargeResult computeDeliveryCharge(Restaurant restaurant, boolean isSelfPickup, boolean freeDelivery,
                                                        String customerLatitude, String customerLongitude) {
        BigDecimal distanceKm = distanceKm(restaurant, customerLatitude, customerLongitude);

        if (isSelfPickup) {
            return new DeliveryChargeResult(BigDecimal.ZERO, distanceKm, "SELF_PICKUP");
        }
        if (freeDelivery) {
            return new DeliveryChargeResult(BigDecimal.ZERO, distanceKm, "FREE_DELIVERY_COUPON");
        }
        if ("dynamic".equalsIgnoreCase(restaurant.getDeliveryChargeType()) && restaurant.getBaseDeliveryCharge() != null) {
            BigDecimal charge = restaurant.getBaseDeliveryCharge();
            int baseDistance = restaurant.getBaseDeliveryDistance() != null ? restaurant.getBaseDeliveryDistance() : 0;
            Integer extraDistanceStep = restaurant.getExtraDeliveryDistance();
            int extraUnits = 0;
            if (distanceKm.doubleValue() > baseDistance && extraDistanceStep != null && extraDistanceStep > 0
                    && restaurant.getExtraDeliveryCharge() != null) {
                double extraKm = distanceKm.doubleValue() - baseDistance;
                extraUnits = (int) Math.ceil(extraKm / extraDistanceStep);
                charge = charge.add(restaurant.getExtraDeliveryCharge().multiply(BigDecimal.valueOf(extraUnits)));
            }
            log.debug("Computed dynamic delivery charge {} for restaurant {} at distance {}km", charge, restaurant.getId(), distanceKm);
            return new DeliveryChargeResult(charge, distanceKm, "DYNAMIC", new DeliveryChargeRates(null, restaurant.getBaseDeliveryCharge(),
                    baseDistance, restaurant.getExtraDeliveryCharge(), extraDistanceStep, extraUnits));
        }
        BigDecimal flat = restaurant.getDeliveryCharges() != null ? restaurant.getDeliveryCharges() : BigDecimal.ZERO;
        return new DeliveryChargeResult(flat, distanceKm, "FIXED", new DeliveryChargeRates(flat, null, null, null, null, null));
    }

    /** Standalone distance lookup - lets a caller (e.g. cart-validation rules) know the distance before/independent of computing a delivery charge from it. Null customer coordinates (no address chosen yet) yield zero, same fallback {@link #computeDeliveryCharge} already had, since every {@link DistanceCalculator} implementation guarantees that on unparseable input. */
    public BigDecimal distanceKm(Restaurant restaurant, String customerLatitude, String customerLongitude) {
        return distanceCalculator.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(), customerLatitude, customerLongitude);
    }

    /** Straight-line distance between any two points (e.g. the rider's last GPS fix and a restaurant). Zero on missing/unparseable input. */
    public BigDecimal distanceKm(String lat1, String lng1, String lat2, String lng2) {
        return distanceCalculator.distanceKm(lat1, lng1, lat2, lng2);
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
