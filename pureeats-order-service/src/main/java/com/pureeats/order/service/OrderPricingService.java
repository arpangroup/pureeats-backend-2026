package com.pureeats.order.service;

import com.pureeats.catalog.service.AppConfigService;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.dto.DeliveryChargeRates;
import com.pureeats.order.dto.DeliveryChargeResult;
import com.pureeats.order.dto.PlatformFeeResult;
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

    /**
     * Platform fee for an order, per Settings -> General -> Platform fee: FLAT = the same ₹ amount on every
     * order; PERCENTAGE = a % of {@code amountAfterDiscount}, optionally capped. Before an admin saves those
     * settings, the flat fee falls back to the older App config "Platform fee (₹)" value, so existing
     * deployments keep charging what they did.
     */
    public PlatformFeeResult platformFee(BigDecimal amountAfterDiscount) {
        String type = java.util.Objects.requireNonNullElse(settingValueService.getString(SettingSchemaService.PLATFORM_FEE_TYPE, PlatformFeeResult.FLAT), PlatformFeeResult.FLAT).toUpperCase();
        if (PlatformFeeResult.PERCENTAGE.equals(type)) {
            BigDecimal percent = nonNegative(settingValueService.getString(SettingSchemaService.PLATFORM_FEE_PERCENTAGE, null), BigDecimal.ZERO);
            BigDecimal cap = nonNegative(settingValueService.getString(SettingSchemaService.PLATFORM_FEE_MAX_AMOUNT, null), BigDecimal.ZERO);
            BigDecimal fee = percentOf(amountAfterDiscount, percent);
            boolean capped = cap.signum() > 0 && fee.compareTo(cap) > 0;
            return new PlatformFeeResult(capped ? cap.setScale(2, RoundingMode.HALF_UP) : fee, PlatformFeeResult.PERCENTAGE, percent,
                    cap.signum() > 0 ? cap : null, capped);
        }
        String flatSetting = settingValueService.getString(SettingSchemaService.PLATFORM_FEE_AMOUNT, null);
        BigDecimal flat = flatSetting != null ? nonNegative(flatSetting, BigDecimal.ZERO) : appConfigService.getPlatformFee();
        flat = flat != null ? flat.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new PlatformFeeResult(flat, PlatformFeeResult.FLAT, flat, null, false);
    }

    /** The store's own commission rate, or Settings -> General -> Commerce -> Default commission (%) when it has none. */
    public BigDecimal commissionPercentage(Restaurant restaurant) {
        if (restaurant != null && restaurant.getCommissionRate() != null && restaurant.getCommissionRate().signum() > 0) {
            return restaurant.getCommissionRate();
        }
        return nonNegative(settingValueService.getString(SettingSchemaService.DEFAULT_COMMISSION_RATE, null), BigDecimal.valueOf(15));
    }

    /** The partner's own commission rate, or Settings -> Delivery Application -> Earnings -> Default delivery partner commission (%) when they have none. */
    public BigDecimal riderCommissionRate(com.pureeats.domain.entity.DeliveryGuyDetail rider) {
        if (riderHasOwnRate(rider)) {
            return rider.getCommissionRate();
        }
        return nonNegative(settingValueService.getString(SettingSchemaService.DEFAULT_RIDER_COMMISSION_RATE, null), BigDecimal.TEN);
    }

    public boolean riderHasOwnRate(com.pureeats.domain.entity.DeliveryGuyDetail rider) {
        return rider != null && rider.getCommissionRate() != null && rider.getCommissionRate().signum() > 0;
    }

    /** Commission on the item total (before any coupon - discounts are platform-funded, so the restaurant's base isn't reduced by them). */
    public BigDecimal commission(BigDecimal itemTotal, BigDecimal commissionPercentage) {
        return percentOf(itemTotal, commissionPercentage);
    }

    /** What the restaurant is paid for an order: item total − commission + its packaging (restaurant) charge. */
    public BigDecimal restaurantPayout(BigDecimal itemTotal, BigDecimal commission, BigDecimal restaurantCharge) {
        BigDecimal payout = itemTotal.subtract(commission).add(restaurantCharge != null ? restaurantCharge : BigDecimal.ZERO);
        return payout.signum() < 0 ? BigDecimal.ZERO : payout.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(String raw, BigDecimal fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            return v.signum() < 0 ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
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
