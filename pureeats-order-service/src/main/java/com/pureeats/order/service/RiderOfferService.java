package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.RiderOfferOverviewResponse;
import com.pureeats.order.dto.RiderOfferSummaryResponse;
import com.pureeats.order.entity.RiderOfferRun;
import com.pureeats.order.entity.RiderOrderDismissal;
import com.pureeats.order.entity.RiderOrderOffer;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.RiderOfferRunRepository;
import com.pureeats.order.repository.RiderOrderDismissalRepository;
import com.pureeats.order.repository.RiderOrderOfferRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Offering a new order to delivery partners - and offering it AGAIN while nobody has taken it.
 * <ul>
 *   <li>{@link #offer} alerts the partners the dispatch rule allows (push + the available-orders list), counts the
 *       offer on the order ({@code rider_offer_count}, {@code last_rider_offer_at}) and records who was alerted
 *       ({@code rider_order_offers}).</li>
 *   <li>A partner who dismisses the alert ({@link #dismiss}) no longer sees the order - until it's offered again: a
 *       dismissal counts for the round it was made in.</li>
 *   <li>{@link #reofferUnassigned} (run every minute by RiderReofferScheduler): an order still without a partner is
 *       offered again once Settings -> Delivery Application -> New order alerts -> "Alert again every" has passed
 *       since the last offer, until "Maximum alerts per order", or forever ("Until a partner accepts"). When the alerts
 *       run out, admins are told once. Each run is recorded ({@code rider_offer_runs}) for the admin's Partner alerts page.</li>
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
    /** RiderReofferScheduler's tick - the next run is about this long after the last. */
    public static final long TICK_SECONDS = 60;

    private final OrderNotificationService orderNotificationService;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final OrderStatusService orderStatusService;
    private final SettingValueService settingValueService;
    private final RiderOrderDismissalRepository dismissalRepository;
    private final RiderOrderOfferRepository offerRepository;
    private final RiderOfferRunRepository runRepository;
    private final UserRepository userRepository;
    private final RiderDispatchService riderDispatchService;

    /** Same window as the available-orders list - older orders aren't offered any more. */
    @Value("${pureeats.delivery.available-order-window-hours:2}")
    private long availableOrderWindowHours;

    /** Alerts the partners who may take this restaurant's orders, records who, and counts the offer. @return partners alerted */
    @Transactional
    public int offer(Order order, Restaurant restaurant) {
        int round = (order.getRiderOfferCount() != null ? order.getRiderOfferCount() : 0) + 1;
        LocalDateTime now = LocalDateTime.now();
        order.setRiderOfferCount(round);
        order.setLastRiderOfferAt(now);
        orderRepository.save(order);
        List<Long> riders = orderNotificationService.notifyDeliveryPartnersOfAvailableOrder(order.getId(), restaurant, order.getPayable(), round);
        for (Long riderUserId : riders) {
            RiderOrderOffer o = new RiderOrderOffer();
            o.setOrderId(order.getId());
            o.setOfferRound(round);
            o.setRiderUserId(riderUserId);
            o.setNotifiedAt(now);
            offerRepository.save(o);
        }
        return riders.size();
    }

    /** The partner dismissed the alert - hide the order from their available orders until it's offered again. */
    @Transactional
    public void dismiss(Long riderUserId, Long orderId) {
        Integer current = orderRepository.findById(orderId).map(Order::getRiderOfferCount).orElse(null);
        int round = current != null ? current : 1;
        RiderOrderDismissal d = dismissalRepository.findByOrderIdAndRiderUserId(orderId, riderUserId).orElseGet(RiderOrderDismissal::new);
        d.setOrderId(orderId);
        d.setRiderUserId(riderUserId);
        d.setOfferRound(Math.max(round, 1));
        d.setDismissedAt(LocalDateTime.now());
        dismissalRepository.save(d);
        log.info("Rider {} dismissed order {} (offer round {})", riderUserId, orderId, round);
    }

    /** Order id -> the offer round this partner dismissed. Hidden while that's the order's current round. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> dismissedRounds(Long riderUserId) {
        Map<Long, Integer> out = new HashMap<>();
        for (RiderOrderDismissal d : dismissalRepository.findByRiderUserId(riderUserId)) {
            out.put(d.getOrderId(), d.getOfferRound() != null ? d.getOfferRound() : 1);
        }
        return out;
    }

    /** Offer again the orders nobody has taken (see the class doc). Called every minute by RiderReofferScheduler. */
    @Transactional
    public void reofferUnassigned() {
        LocalDateTime now = LocalDateTime.now();
        Settings s = settings();
        List<Order> waiting = waitingOrders(now);
        RiderOfferRun run = new RiderOfferRun();
        run.setRanAt(now);
        run.setEnabled(s.enabled());
        run.setWaitingOrders(waiting.size());
        int reoffered = 0;
        int partners = 0;
        int adminAlerts = 0;
        List<String> ids = new ArrayList<>();
        if (s.enabled()) {
            for (Order order : waiting) {
                int offers = order.getRiderOfferCount() != null ? order.getRiderOfferCount() : 0;
                LocalDateTime last = order.getLastRiderOfferAt() != null ? order.getLastRiderOfferAt() : order.getUpdatedAt();
                if (last != null && last.isAfter(now.minusMinutes(s.intervalMinutes()))) continue;
                Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
                if (restaurant == null) continue;
                if (!s.untilAssigned() && offers >= s.maxOffers()) {
                    if (alertAdminsOnce(order, restaurant, offers, now)) adminAlerts++;
                    continue;
                }
                partners += offer(order, restaurant);
                reoffered++;
                ids.add(order.getUniqueOrderId());
            }
        }
        run.setReoffered(reoffered);
        run.setPartnersAlerted(partners);
        run.setAdminAlerts(adminAlerts);
        String joined = String.join(",", ids);
        run.setReofferedOrders(joined.length() > 2000 ? joined.substring(0, 2000) : joined);
        runRepository.save(run);
        runRepository.deleteOlderThan(now.minusDays(3));
        if (reoffered > 0) log.info("Re-offered {} unassigned order(s) to {} delivery partner(s)", reoffered, partners);
    }

    /** The admin's Partner alerts page. */
    @Transactional(readOnly = true)
    public RiderOfferOverviewResponse overview() {
        LocalDateTime now = LocalDateTime.now();
        Settings s = settings();
        List<RiderOfferRun> runs = runRepository.findTop30ByOrderByRanAtDesc();
        LocalDateTime lastRun = runs.isEmpty() ? null : runs.get(0).getRanAt();
        List<RiderOfferOverviewResponse.WaitingOrder> waiting = new ArrayList<>();
        for (Order o : waitingOrders(now)) {
            Restaurant r = restaurantRepository.findById(o.getRestaurantId().longValue()).orElse(null);
            OrderStatusCode code = orderStatusService.codeFor(o.getOrderstatusId());
            waiting.add(new RiderOfferOverviewResponse.WaitingOrder(o.getId(), o.getUniqueOrderId(), r != null ? r.getName() : "Unknown",
                    code != null ? code.name() : "UNKNOWN", o.getPayable(), o.getPaymentMode(), o.getCreatedAt(), summarize(o, s, now)));
        }
        return new RiderOfferOverviewResponse(s.enabled(), s.intervalMinutes(), s.untilAssigned() ? MODE_UNTIL_ASSIGNED : MODE_LIMITED,
                s.maxOffers(), riderDispatchService.mode(), lastRun, lastRun != null ? lastRun.plusSeconds(TICK_SECONDS) : null,
                runs.stream().map(r -> new RiderOfferOverviewResponse.Run(r.getRanAt(), Boolean.TRUE.equals(r.getEnabled()), r.getWaitingOrders(),
                        r.getReoffered(), r.getPartnersAlerted(), r.getAdminAlerts(),
                        r.getReofferedOrders() == null || r.getReofferedOrders().isBlank() ? List.of() : List.of(r.getReofferedOrders().split(",")))).toList(),
                waiting);
    }

    /** One order's alerts - for the admin's order page. */
    @Transactional(readOnly = true)
    public RiderOfferSummaryResponse summary(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new com.pureeats.domain.common.exception.ResourceNotFoundException("Order not found: " + orderId));
        return summarize(order, settings(), LocalDateTime.now());
    }

    private RiderOfferSummaryResponse summarize(Order order, Settings s, LocalDateTime now) {
        List<RiderOrderOffer> offers = offerRepository.findByOrderIdOrderByOfferRoundAscNotifiedAtAsc(order.getId());
        List<RiderOrderDismissal> dismissals = dismissalRepository.findByOrderIdOrderByDismissedAtAsc(order.getId());
        Set<Long> userIds = new java.util.HashSet<>();
        offers.forEach(o -> userIds.add(o.getRiderUserId()));
        dismissals.forEach(d -> userIds.add(d.getRiderUserId()));
        Map<Long, User> users = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        Map<Integer, List<RiderOrderOffer>> byRound = new LinkedHashMap<>();
        offers.forEach(o -> byRound.computeIfAbsent(o.getOfferRound(), k -> new ArrayList<>()).add(o));
        // Every round, also one that reached nobody (no eligible partner online) - it has no rows of its own.
        int count = order.getRiderOfferCount() != null ? order.getRiderOfferCount() : 0;
        int lastRound = Math.max(count, byRound.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
        List<RiderOfferSummaryResponse.Round> rounds = new ArrayList<>();
        for (int r = 1; r <= lastRound; r++) {
            List<RiderOrderOffer> rows = byRound.getOrDefault(r, List.of());
            LocalDateTime at = !rows.isEmpty() ? rows.get(0).getNotifiedAt() : (r == count ? order.getLastRiderOfferAt() : null);
            rounds.add(new RiderOfferSummaryResponse.Round(r, at, rows.stream().map(o -> partner(o.getRiderUserId(), users)).toList()));
        }
        List<RiderOfferSummaryResponse.Dismissal> dismissed = dismissals.stream()
                .map(d -> new RiderOfferSummaryResponse.Dismissal(partner(d.getRiderUserId(), users), d.getOfferRound() != null ? d.getOfferRound() : 1, d.getDismissedAt()))
                .toList();

        String state;
        LocalDateTime next = null;
        OrderStatusCode code = orderStatusService.codeFor(order.getOrderstatusId());
        boolean offerable = order.getDeliveryType() != null && order.getDeliveryType() == 0
                && (code == OrderStatusCode.RESTAURANT_ACCEPTED || code == OrderStatusCode.READY_FOR_PICKUP);
        boolean assigned = acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent();
        if (assigned) state = "ASSIGNED";
        else if (!offerable) state = count > 0 ? "ENDED" : "NOT_OFFERED";
        else if (!s.enabled()) state = "DISABLED";
        else if (!s.untilAssigned() && count >= s.maxOffers()) state = "STOPPED_MAX";
        else {
            state = "WAITING";
            LocalDateTime last = order.getLastRiderOfferAt() != null ? order.getLastRiderOfferAt() : order.getUpdatedAt();
            next = last != null ? last.plusMinutes(s.intervalMinutes()) : now;
            if (next.isBefore(now)) next = now;
        }
        return new RiderOfferSummaryResponse(state, count, order.getLastRiderOfferAt(), next, s.intervalMinutes(),
                s.untilAssigned() ? MODE_UNTIL_ASSIGNED : MODE_LIMITED, s.maxOffers(), riderDispatchService.mode(),
                order.getNoRiderAlertedAt(), rounds, dismissed);
    }

    private static RiderOfferSummaryResponse.Partner partner(Long userId, Map<Long, User> users) {
        User u = users.get(userId);
        return new RiderOfferSummaryResponse.Partner(userId, u != null ? u.getName() : "Partner #" + userId, u != null ? u.getPhone() : null);
    }

    /** Delivery orders the restaurant has accepted that no partner has taken yet (within the available-orders window). */
    private List<Order> waitingOrders(LocalDateTime now) {
        List<Integer> statusIds = List.of(
                orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED),
                orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP));
        return orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(statusIds, now.minusHours(availableOrderWindowHours))
                .stream()
                .filter(o -> o.getDeliveryType() != null && o.getDeliveryType() == 0)
                .filter(o -> acceptDeliveryRepository.findByOrderId(o.getId().intValue()).isEmpty())
                .toList();
    }

    private record Settings(boolean enabled, int intervalMinutes, boolean untilAssigned, int maxOffers) {
    }

    private Settings settings() {
        return new Settings(
                settingValueService.getBoolean(SettingSchemaService.RIDER_REOFFER_ENABLED, true),
                Math.max(1, settingValueService.getInt(SettingSchemaService.RIDER_REOFFER_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)),
                MODE_UNTIL_ASSIGNED.equalsIgnoreCase(settingValueService.getString(SettingSchemaService.RIDER_REOFFER_MODE, MODE_LIMITED)),
                Math.max(1, settingValueService.getInt(SettingSchemaService.RIDER_REOFFER_MAX_OFFERS, DEFAULT_MAX_OFFERS)));
    }

    private boolean alertAdminsOnce(Order order, Restaurant restaurant, int offers, LocalDateTime now) {
        if (order.getNoRiderAlertedAt() != null) return false;
        order.setNoRiderAlertedAt(now);
        orderRepository.save(order);
        orderNotificationService.notifyAdminsOfNewOrder("No delivery partner yet",
                "Order #" + order.getUniqueOrderId() + " at " + restaurant.getName() + " is still waiting for a delivery partner after "
                        + offers + " alert" + (offers == 1 ? "" : "s") + " - assign one from the order page.",
                Map.of("orderId", order.getId(), "uniqueOrderId", order.getUniqueOrderId(), "restaurantName", restaurant.getName(), "reason", "NO_RIDER"));
        log.warn("Order {} still has no delivery partner after {} offers - admins alerted", order.getId(), offers);
        return true;
    }
}
