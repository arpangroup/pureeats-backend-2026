package com.pureeats.order.service;

import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.DeliveryGuyRestaurant;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.geo.distance.DistanceCalculator;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.DeliveryGuyRestaurantRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which delivery partners hear about a new order and may accept it themselves - Settings -> Delivery
 * Application -> New order alerts. One rule behind the new-order push, the partner's available-orders list (which
 * also drives the in-app alert) and self-accept, so they can never disagree. An admin assigning an order directly
 * is not limited by it.
 * <ul>
 *   <li>{@link #LINKED_STORES} (default): partners an admin linked to that restaurant. A store with nobody linked
 *       falls back to {@link #NEARBY} when "offer to nearby partners" is on, else reaches no one.</li>
 *   <li>{@link #NEARBY}: partners whose last reported location is within the range of the restaurant
 *       (straight line - this runs for every online partner, so it's never a paid lookup).</li>
 *   <li>{@link #ALL}: every approved online partner (the old behaviour).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiderDispatchService {

    public static final String LINKED_STORES = "LINKED_STORES";
    public static final String NEARBY = "NEARBY";
    public static final String ALL = "ALL";
    static final double DEFAULT_RADIUS_KM = 5;

    private final SettingValueService settingValueService;
    private final DeliveryGuyRestaurantRepository deliveryGuyRestaurantRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final UserRepository userRepository;
    private final DistanceCalculator distanceCalculator;

    public String mode() {
        String mode = settingValueService.getString(SettingSchemaService.RIDER_DISPATCH_MODE, LINKED_STORES).toUpperCase(java.util.Locale.ROOT);
        return switch (mode) {
            case NEARBY, ALL -> mode;
            default -> LINKED_STORES;
        };
    }

    /** May this partner see and self-accept this restaurant's new orders? */
    @Transactional(readOnly = true)
    public boolean canReceive(DeliveryGuyDetail rider, Restaurant restaurant) {
        return new Rule(mode(), linkedStoreIds(rider)).allows(rider, restaurant);
    }

    /**
     * The rule for one partner, resolved once - for filtering a whole list of orders without re-reading the
     * settings and the partner's links per order.
     */
    @Transactional(readOnly = true)
    public java.util.function.Predicate<Restaurant> ruleFor(DeliveryGuyDetail rider) {
        Rule rule = new Rule(mode(), linkedStoreIds(rider));
        return restaurant -> rule.allows(rider, restaurant);
    }

    /** User ids of the online, approved, active partners who should be alerted about this restaurant's new order. */
    @Transactional(readOnly = true)
    public List<Long> recipientUserIds(Restaurant restaurant) {
        String mode = mode();
        List<Long> out = new java.util.ArrayList<>();
        for (DeliveryGuyDetail rider : deliveryGuyDetailRepository.findByIsOnlineTrue()) {
            if (!rider.isApproved() || Boolean.FALSE.equals(rider.getIsActive())) continue;
            if (!new Rule(mode, linkedStoreIds(rider)).allows(rider, restaurant)) continue;
            userRepository.findByDeliveryGuyDetailId(rider.getId().intValue())
                    .filter(RiderDispatchService::accountActive)
                    .ifPresent(u -> out.add(u.getId()));
        }
        log.debug("New order at restaurant {} ({} mode): {} partner(s) to alert", restaurant.getId(), mode, out.size());
        return out;
    }

    private Set<Long> linkedStoreIds(DeliveryGuyDetail rider) {
        if (rider.getId() == null) return Set.of();
        return deliveryGuyRestaurantRepository.findByDeliveryGuyDetailId(rider.getId()).stream()
                .map(DeliveryGuyRestaurant::getRestaurantId)
                .collect(Collectors.toSet());
    }

    private static boolean accountActive(User u) {
        AccountStatus status = u.getAccountStatus() != null ? u.getAccountStatus() : AccountStatus.ACTIVE;
        return status == AccountStatus.ACTIVE && !User.STATUS_INACTIVE.equalsIgnoreCase(u.getIsActive());
    }

    private final class Rule {
        private final String mode;
        private final Set<Long> linked;

        Rule(String mode, Set<Long> linked) {
            this.mode = mode;
            this.linked = linked;
        }

        boolean allows(DeliveryGuyDetail rider, Restaurant restaurant) {
            if (restaurant == null) return false;
            return switch (mode) {
                case ALL -> true;
                case NEARBY -> nearby(rider, restaurant);
                default -> linked.contains(restaurant.getId())
                        || (fallbackToNearby() && !deliveryGuyRestaurantRepository.existsByRestaurantId(restaurant.getId()) && nearby(rider, restaurant));
            };
        }
    }

    private boolean fallbackToNearby() {
        return settingValueService.getBoolean(SettingSchemaService.RIDER_DISPATCH_FALLBACK_NEARBY, true);
    }

    private boolean nearby(DeliveryGuyDetail rider, Restaurant restaurant) {
        if (rider.getLastLat() == null || rider.getLastLng() == null) return false;
        double radius = radiusKm();
        BigDecimal km = distanceCalculator.straightLineKm(rider.getLastLat().toPlainString(), rider.getLastLng().toPlainString(),
                restaurant.getLatitude(), restaurant.getLongitude());
        return km.doubleValue() <= radius;
    }

    private double radiusKm() {
        String raw = settingValueService.getString(SettingSchemaService.RIDER_DISPATCH_RADIUS_KM, null);
        try {
            double v = raw != null ? Double.parseDouble(raw) : DEFAULT_RADIUS_KM;
            return v > 0 ? v : DEFAULT_RADIUS_KM;
        } catch (NumberFormatException e) {
            return DEFAULT_RADIUS_KM;
        }
    }
}
