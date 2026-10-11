package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.entity.RiderOrderDismissal;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.RiderOrderDismissalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offering a new order to delivery partners - and offering it AGAIN while nobody has taken it.
 * <ul>
 *   <li>{@link #offer} alerts the partners the dispatch rule allows (push + the available-orders list) and counts
 *       the offer on the order ({@code rider_offer_count}, {@code last_rider_offer_at}).</li>
 *   <li>A partner who dismisses the alert ({@link #dismiss}) no longer sees the order in their available orders.</li>
 *   <li>{@link #reofferUnassigned} runs every minute: an order still without a partner gets offered again once
 *       Settings -> Delivery Application -> New order alerts -> "Alert again every" has passed since the last
 *       offer - which also brings it back for partners who dismissed it - until "Stop after" offers, or forever
 *       ("Keep alerting until a partner accepts"). When the offers run out, admins are told once so someone can
 *       assign a partner by hand.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiderOfferService {

    static final int DEFAULT_INTERVAL_MINUTES = 2;
    static final int DEFAULT_MAX_OFFERS = 5;
    public static final String MODE_LIMITED = "LIMITED";
    public static final String MODE_UNTIL_ASSIGNED = "UNTIL_ASSIGNED";

    private final OrderNotificationService orderNotificationService;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final OrderStatusService orderStatusService;
    private final SettingValueService settingValueService;
    private final RiderOrderDismissalRepository dismissalRepository;

    /** Same window as the available-orders list - older orders aren't offered any more. */
    @Value("${pureeats.delivery.available-order-window-hours:2}")
    private long availableOrderWindowHours;

    /** Alerts the partners who may take this restaurant's orders and records the offer on the order. */
    @Transactional
    public void offer(Order order, Restaurant restaurant) {
        int round = (order.getRiderOfferCount() != null ? order.getRiderOfferCount() : 0) + 1;
        order.setRiderOfferCount(round);
        order.setLastRiderOfferAt(LocalDateTime.now());
        orderRepository.save(order);
        orderNotificationService.notifyDeliveryPartnersOfAvailableOrder(order.getId(), restaurant, order.getPayable(), round);
    }

    /** The partner dismissed the alert - hide the order from their available orders until it's offered again. */
    @Transactional
    public void dismiss(Long riderUserId, Long orderId) {
        if (dismissalRepository.existsByOrderIdAndRiderUserId(orderId, riderUserId)) return;
        RiderOrderDismissal d = new RiderOrderDismissal();
        d.setOrderId(orderId);
        d.setRiderUserId(riderUserId);
        d.setDismissedAt(LocalDateTime.now());
        dismissalRepository.save(d);
        log.info("Rider {} dismissed order {}", riderUserId, orderId);
    }

    @Transactional(readOnly = true)
    public Set<Long> dismissedOrderIds(Long riderUserId) {
        return new HashSet<>(dismissalRepository.findOrderIdsByRiderUserId(riderUserId));
    }

    /** Every minute: offer again the orders nobody has taken (see the class doc). */
    @Scheduled(fixedDelayString = "${pureeats.delivery.reoffer-check-interval-ms:60000}",
            initialDelayString = "${pureeats.delivery.reoffer-check-interval-ms:60000}")
    @Transactional
    public void reofferUnassigned() {
        if (!settingValueService.getBoolean(SettingSchemaService.RIDER_REOFFER_ENABLED, true)) return;
        LocalDateTime now = LocalDateTime.now();
        int interval = Math.max(1, settingValueService.getInt(SettingSchemaService.RIDER_REOFFER_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES));
        boolean untilAssigned = MODE_UNTIL_ASSIGNED.equalsIgnoreCase(settingValueService.getString(SettingSchemaService.RIDER_REOFFER_MODE, MODE_LIMITED));
        int maxOffers = Math.max(1, settingValueService.getInt(SettingSchemaService.RIDER_REOFFER_MAX_OFFERS, DEFAULT_MAX_OFFERS));

        List<Integer> statusIds = List.of(
                orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED),
                orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP));
        List<Order> waiting = orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                statusIds, now.minusHours(availableOrderWindowHours));
        int reoffered = 0;
        for (Order order : waiting) {
            if (order.getDeliveryType() == null || order.getDeliveryType() != 0) continue;
            if (acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent()) continue;
            int offers = order.getRiderOfferCount() != null ? order.getRiderOfferCount() : 0;
            LocalDateTime last = order.getLastRiderOfferAt() != null ? order.getLastRiderOfferAt() : order.getUpdatedAt();
            if (last != null && last.isAfter(now.minusMinutes(interval))) continue;
            Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
            if (restaurant == null) continue;

            if (!untilAssigned && offers >= maxOffers) {
                alertAdminsOnce(order, restaurant, offers, now);
                continue;
            }
            // A fresh offer brings the order back for partners who dismissed it.
            dismissalRepository.deleteByOrderId(order.getId());
            offer(order, restaurant);
            reoffered++;
        }
        if (reoffered > 0) log.info("Re-offered {} unassigned order(s) to delivery partners", reoffered);
    }

    private void alertAdminsOnce(Order order, Restaurant restaurant, int offers, LocalDateTime now) {
        if (order.getNoRiderAlertedAt() != null) return;
        order.setNoRiderAlertedAt(now);
        orderRepository.save(order);
        orderNotificationService.notifyAdminsOfNewOrder("No delivery partner yet",
                "Order #" + order.getUniqueOrderId() + " at " + restaurant.getName() + " is still waiting for a delivery partner after "
                        + offers + " alert" + (offers == 1 ? "" : "s") + " - assign one from the order page.",
                Map.of("orderId", order.getId(), "uniqueOrderId", order.getUniqueOrderId(), "restaurantName", restaurant.getName(), "reason", "NO_RIDER"));
        log.warn("Order {} still has no delivery partner after {} offers - admins alerted", order.getId(), offers);
    }
}
