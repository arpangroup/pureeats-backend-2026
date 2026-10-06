package com.pureeats.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.notification.enums.NotificationRecipientRole;
import com.pureeats.order.dto.*;
import com.pureeats.order.repository.*;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.DeliveryGuyLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryOrderService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final GpsTableRepository gpsTableRepository;
    private final TripDetailRepository tripDetailRepository;
    private final DeliveryCollectionRepository deliveryCollectionRepository;
    private final DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    private final RestaurantPayoutService restaurantPayoutService;
    private final WalletService walletService;
    private final OrderNotificationService orderNotificationService;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final OrderStatusLogService orderStatusLogService;
    private final DeliveryGuyLocationService deliveryGuyLocationService;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPricingService orderPricingService;
    private final ObjectMapper objectMapper;
    private final OrderStatusLogRepository orderStatusLogRepository;

    /** Statuses after which an assignment is no longer "active" even if its AcceptDelivery row was never marked complete (e.g. the customer cancelled after a rider was assigned). */
    private static final java.util.Set<OrderStatusCode> TERMINAL_STATUSES = java.util.EnumSet.of(
            OrderStatusCode.DELIVERED, OrderStatusCode.SELF_PICKUP_COMPLETED, OrderStatusCode.CANCELLED,
            OrderStatusCode.REJECTED, OrderStatusCode.RETURNED, OrderStatusCode.AUTO_CANCELLED);

    @Value("${pureeats.commission.basis:FULL_ORDER}")
    private CommissionBasis commissionBasis;

    /** An order still sitting unassigned in RESTAURANT_ACCEPTED/READY_FOR_PICKUP past this age is
     * treated as stale (abandoned, or demo/seed data) rather than genuinely available - see
     * {@link #availableOrders}. Ops can reassign a specific stale order by hand (admin override)
     * regardless of this window; this only bounds what's surfaced to riders browsing/polling. */
    @Value("${pureeats.delivery.available-order-window-hours:2}")
    private long availableOrderWindowHours;

    /**
     * Rider-scoped (not just role-scoped): {@code payoutEstimate} is computed against the CALLING
     * rider's own commission rate, mirroring {@link #creditRiderAndSettle}'s math exactly, so what's
     * shown here is what they'd actually be credited if they accept - two riders with different
     * commission rates would see different payout numbers for the same order.
     */
    @Transactional
    public List<DeliveryAvailableOrderResponse> availableOrders(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        List<Integer> statusIds = List.of(
                orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED),
                orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP));
        LocalDateTime cutoff = LocalDateTime.now().minusHours(availableOrderWindowHours);
        return orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(statusIds, cutoff).stream()
                .filter(o -> o.getDeliveryType() == 0 && acceptDeliveryRepository.findByOrderId(o.getId().intValue()).isEmpty())
                .map(o -> toAvailableOrderResponse(o, rider))
                .toList();
    }

    private DeliveryAvailableOrderResponse toAvailableOrderResponse(Order order, DeliveryGuyDetail rider) {
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        String customerLat = null;
        String customerLng = null;
        try {
            if (order.getLocation() != null) {
                JsonNode node = objectMapper.readTree(order.getLocation());
                customerLat = node.path("latitude").asText(null);
                customerLng = node.path("longitude").asText(null);
            }
        } catch (Exception e) {
            log.warn("Could not parse stored location JSON for order {}: {}", order.getId(), e.getMessage());
        }

        BigDecimal distanceKm = restaurant != null
                ? orderPricingService.distanceKm(restaurant, customerLat, customerLng)
                : BigDecimal.ZERO;

        BigDecimal commissionBase = commissionBasis == CommissionBasis.DELIVERY_CHARGE_ONLY
                ? order.getDeliveryCharge() : order.getTotal();
        BigDecimal payoutEstimate = commissionBase.multiply(rider.getCommissionRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        int itemsCount = orderItemRepository.findByOrderId(order.getId().intValue()).size();

        return new DeliveryAvailableOrderResponse(
                order.getId(), order.getUniqueOrderId(),
                restaurant != null ? restaurant.getName() : "Unknown",
                restaurant != null ? restaurant.getAddress() : "",
                restaurant != null ? parseCoordinate(restaurant.getLatitude()) : BigDecimal.ZERO,
                restaurant != null ? parseCoordinate(restaurant.getLongitude()) : BigDecimal.ZERO,
                order.getAddress(),
                parseCoordinate(customerLat), parseCoordinate(customerLng),
                distanceKm, payoutEstimate, itemsCount, order.getCreatedAt());
    }

    private BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    public OrderResponse acceptToDeliver(Long riderUserId, Long orderId) {
        log.info("Rider {} accepting order {} for delivery", riderUserId, orderId);
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        Order order = orderService.findOrThrow(orderId);
        if (acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent()) {
            log.warn("Rejected delivery acceptance for order {}: already assigned to a rider", orderId);
            throw new BadRequestException("This order has already been assigned to a rider");
        }
        long activeCount = countDeliveriesInProgress(riderUserId);
        int limit = rider.getMaxAcceptDeliveryLimit() != null && rider.getMaxAcceptDeliveryLimit() > 0 ? rider.getMaxAcceptDeliveryLimit() : 1;
        if (activeCount >= limit) {
            log.warn("Rejected delivery acceptance for rider {}: at concurrent delivery limit ({}/{})",
                    riderUserId, activeCount, limit);
            throw new BadRequestException("You have reached your maximum concurrent delivery limit");
        }

        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(order.getId().intValue());
        accept.setUserId(riderUserId.intValue());
        accept.setCustomerId(order.getUserId());
        accept.setIsComplete(false);
        acceptDeliveryRepository.save(accept);

        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.RIDER_ASSIGNED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.RIDER_ASSIGNED, "DELIVERY", riderUserId, null);
        log.info("Order {} transitioned {} -> RIDER_ASSIGNED (rider {} self-accepted)", orderId, from, riderUserId);

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId(),
                riderAssignedData(order.getId(), rider, riderUserId));
        return orderService.toResponse(order);
    }

    /** Admin override - assigns a specific rider to a specific order directly, skipping the rider's own concurrent-delivery-limit check (an explicit admin decision, not a rider self-service action). */
    @Transactional
    public OrderResponse assignDriverAsAdmin(Long adminUserId, Long orderId, Long riderUserId) {
        log.info("Admin {} assigning rider {} to order {}", adminUserId, riderUserId, orderId);
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        Order order = orderService.findOrThrow(orderId);
        if (acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent()) {
            log.warn("Rejected admin driver assignment for order {}: already assigned to a rider", orderId);
            throw new BadRequestException("This order has already been assigned to a rider");
        }

        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(order.getId().intValue());
        accept.setUserId(riderUserId.intValue());
        accept.setCustomerId(order.getUserId());
        accept.setIsComplete(false);
        acceptDeliveryRepository.save(accept);

        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.RIDER_ASSIGNED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.RIDER_ASSIGNED, "ADMIN", adminUserId, "Driver assigned by admin");
        log.info("Order {} transitioned {} -> RIDER_ASSIGNED (rider {} assigned by admin {})", orderId, from, riderUserId, adminUserId);

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId(),
                riderAssignedData(order.getId(), rider, riderUserId));
        orderNotificationService.notify(NotificationRecipientRole.DELIVERY_PARTNER, riderUserId, "New delivery assigned",
                "You've been assigned to deliver order #" + order.getUniqueOrderId());
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse pickedUp(Long riderUserId, Long orderId) {
        log.info("Rider {} marking order {} as picked up", riderUserId, orderId);
        Order order = ownedByRider(riderUserId, orderId);
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.PICKED_UP));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.PICKED_UP, "DELIVERY", riderUserId, null);
        log.info("Order {} transitioned {} -> PICKED_UP by rider {}", orderId, from, riderUserId);
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse deliver(Long riderUserId, Long orderId, String deliveryPin) {
        log.info("Rider {} attempting to complete delivery for order {}", riderUserId, orderId);
        Order order = ownedByRider(riderUserId, orderId);
        return completeDelivery(order, deliveryPin, "DELIVERY", riderUserId, "Verified by delivery PIN");
    }

    /** The customer confirms delivery themself by reading out the same PIN - e.g. handed to the rider in person. */
    @Transactional
    public OrderResponse customerConfirmDelivery(Long customerUserId, Long orderId, String deliveryPin) {
        log.info("Customer {} attempting to self-confirm delivery for order {}", customerUserId, orderId);
        Order order = orderService.findOrThrow(orderId);
        if (!order.getUserId().equals(customerUserId.intValue())) {
            log.warn("User {} attempted to confirm delivery of order {} which does not belong to them", customerUserId, orderId);
            throw new ForbiddenException("This order does not belong to you");
        }
        return completeDelivery(order, deliveryPin, "CUSTOMER", customerUserId, "Confirmed by customer via delivery PIN");
    }

    /**
     * Shared by both delivery-confirmation paths - whoever confirms it, the assigned rider (if
     * any) is who actually gets credited/logged in the trip, not necessarily the caller.
     */
    private OrderResponse completeDelivery(Order order, String deliveryPin, String actorType, Long actorUserId, String note) {
        if (!order.getDeliveryPin().equalsIgnoreCase(deliveryPin)) {
            log.warn("Rejected delivery completion for order {} by {} {}: incorrect delivery PIN", order.getId(), actorType, actorUserId);
            throw new BadRequestException("Incorrect delivery PIN");
        }

        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.DELIVERED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, OrderStatusCode.DELIVERED, actorType, actorUserId, note);
        log.info("Order {} transitioned {} -> DELIVERED ({} {})", order.getId(), from, actorType, actorUserId);

        AcceptDelivery assignment = acceptDeliveryRepository.findByOrderId(order.getId().intValue()).orElse(null);
        if (assignment != null) {
            assignment.setIsComplete(true);
            acceptDeliveryRepository.save(assignment);
            creditRiderAndSettle(order, assignment.getUserId().longValue());
        } else {
            log.debug("Order {} delivered with no rider assignment on record (likely self-pickup)", order.getId());
        }

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Order delivered",
                "Your order #" + order.getUniqueOrderId() + " has been delivered. Enjoy your meal!",
                Map.of("orderId", order.getId(), "status", OrderStatusCode.DELIVERED.name()));
        return orderService.toResponse(order);
    }

    private void creditRiderAndSettle(Order order, Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        BigDecimal commissionBase = commissionBasis == CommissionBasis.DELIVERY_CHARGE_ONLY
                ? order.getDeliveryCharge() : order.getTotal();
        BigDecimal riderEarning = commissionBase.multiply(rider.getCommissionRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        walletService.credit(riderUserId, riderEarning, "Delivery earning for order #" + order.getUniqueOrderId());
        log.debug("Credited rider {} earning {} for order {}", riderUserId, riderEarning, order.getId());

        BigDecimal restaurantEarning = order.getTotal().subtract(order.getRestaurantCharge());
        restaurantPayoutService.recordEarning(order.getRestaurantId(), restaurantEarning);

        BigDecimal cashCollected = BigDecimal.ZERO;
        if ("COD".equals(order.getPaymentMode())) {
            cashCollected = order.getPayable();
            recordCashCollection(riderUserId, cashCollected, order.getUniqueOrderId());
            log.debug("Recorded COD cash collection of {} for rider {} on order {}", cashCollected, riderUserId, order.getId());
        }

        TripDetail trip = new TripDetail();
        trip.setOrderId(order.getId().intValue());
        trip.setCustomerId(order.getUserId());
        trip.setRestaurantId(order.getRestaurantId());
        trip.setRiderId(riderUserId.intValue());
        trip.setDeliveryCollectionId(0);
        trip.setDistanceTravelled(BigDecimal.ZERO);
        trip.setRiderEarning(riderEarning);
        trip.setRestaurantEarning(restaurantEarning);
        trip.setCashCollectedFromCustomer(cashCollected);
        trip.setCashOnHold(BigDecimal.ZERO);
        trip.setIsSettlementDone(0);
        trip.setCreatedAt(LocalDateTime.now());
        trip.setUpdatedAt(LocalDateTime.now());
        tripDetailRepository.save(trip);
    }

    @Transactional
    public void recordGpsPing(GpsPingRequest request) {
        log.debug("Recording GPS ping for order {}", request.orderId());
        GpsTable gps = new GpsTable();
        gps.setOrderId(request.orderId().intValue());
        gps.setDeliveryLat(request.deliveryLat());
        gps.setDeliveryLong(request.deliveryLong());
        gps.setHeading(request.heading());
        gps.setBearing(request.bearing());
        gps.setCreatedAt(LocalDateTime.now());
        gps.setUpdatedAt(LocalDateTime.now());
        gpsTableRepository.save(gps);
    }

    @Transactional(readOnly = true)
    public GpsLocationResponse getGpsLocation(Long orderId) {
        GpsTable gps = gpsTableRepository.findFirstByOrderIdOrderByUpdatedAtDesc(orderId.intValue())
                .orElseThrow(() -> {
                    log.warn("No GPS ping recorded for order {}", orderId);
                    return new ResourceNotFoundException("No GPS ping recorded for this order yet");
                });
        return new GpsLocationResponse(gps.getDeliveryLat(), gps.getDeliveryLong(), gps.getHeading(), gps.getBearing());
    }

    /**
     * Self-service online/offline toggle for the signed-in rider. Going offline deliberately leaves
     * {@code lastLat}/{@code lastLng}/{@code lastSeenAt} untouched (stale-but-present) rather than
     * clearing them - an admin/ops view showing "last seen at 5:42pm near X" for an offline rider is
     * more useful than showing nothing, and it costs nothing since {@link #availableOrders} /
     * anything rider-nearby-facing should already be filtering on {@code isOnline} itself.
     */
    @Transactional
    public void setOnlineStatus(Long riderUserId, boolean isOnline) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.valueOf(isOnline).equals(rider.getIsOnline())) {
            rider.setStatusChangedAt(now);
        }
        rider.setIsOnline(isOnline);
        rider.setOfflineReason(isOnline ? null : DeliveryGuyDetail.OFFLINE_REASON_SELF);
        // Going online counts as "seen" - otherwise a rider whose first GPS fix is still pending could be
        // swept straight back offline by RiderInactivityScheduler on the strength of an old lastSeenAt.
        if (isOnline) rider.setLastSeenAt(now);
        deliveryGuyDetailRepository.save(rider);
        log.info("Rider {} is now {}", riderUserId, isOnline ? "ONLINE" : "OFFLINE");
    }

    /** The signed-in rider's own server-side status - lets the app notice it was force-stopped (see RiderInactivityScheduler) while backgrounded. */
    @Transactional(readOnly = true)
    public RiderStatusResponse getOnlineStatus(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return new RiderStatusResponse(Boolean.TRUE.equals(rider.getIsOnline()), rider.getOfflineReason(),
                rider.getStatusChangedAt(), rider.getLastSeenAt());
    }

    /**
     * Self-service, principal-scoped location ping - resolves the caller's own {@link
     * DeliveryGuyDetail} the same way every other rider action does ({@link #riderProfile}), then
     * delegates the actual write to {@link DeliveryGuyLocationService#updateLocation}, the same
     * method the admin id-scoped endpoint calls, so the write logic itself isn't duplicated.
     */
    @Transactional
    public void updateMyLocation(Long riderUserId, String lat, String lng) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        deliveryGuyLocationService.updateLocation(rider.getId(), new BigDecimal(lat), new BigDecimal(lng));
    }

    /** The signed-in rider's own delivery history (every assignment, newest first), sourced from {@link AcceptDeliveryRepository} (assignments) rather than {@code OrderRepository#findByUserId} (which is the customer's own orders). */
    @Transactional(readOnly = true)
    public List<DeliveryAssignmentResponse> myOrders(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return acceptDeliveryRepository.findByUserIdOrderByIdDesc(riderUserId.intValue()).stream()
                .map(accept -> orderRepository.findById(accept.getOrderId().longValue()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(order -> toAssignmentResponse(order, rider))
                .toList();
    }

    /**
     * Every order currently assigned to the signed-in rider and not yet finished - including ones an
     * admin assigned to them directly ({@link #assignDriverAsAdmin}), which never pass through the
     * rider's own accept flow and so would otherwise never appear in the app.
     */
    @Transactional(readOnly = true)
    public List<DeliveryAssignmentResponse> activeOrders(Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue()).stream()
                .map(accept -> orderRepository.findById(accept.getOrderId().longValue()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(order -> !TERMINAL_STATUSES.contains(orderStatusService.codeFor(order.getOrderstatusId())))
                .sorted(java.util.Comparator.comparing(Order::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(order -> toAssignmentResponse(order, rider))
                .toList();
    }

    /**
     * Assignments that still genuinely occupy the rider. An AcceptDelivery row is only marked complete
     * on delivery, so one whose order was later cancelled/rejected/returned stays "incomplete"
     * forever - counting those raw (as this used to) permanently ate into the rider's concurrent
     * limit, and a rider with a few such leftovers could never accept another order.
     */
    public long countDeliveriesInProgress(Long riderUserId) {
        return acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue()).stream()
                .map(accept -> orderRepository.findById(accept.getOrderId().longValue()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(order -> !TERMINAL_STATUSES.contains(orderStatusService.codeFor(order.getOrderstatusId())))
                .count();
    }

    private DeliveryAssignmentResponse toAssignmentResponse(Order order, DeliveryGuyDetail rider) {
        Restaurant restaurant = restaurantRepository.findById(order.getRestaurantId().longValue()).orElse(null);
        String customerLat = null;
        String customerLng = null;
        try {
            if (order.getLocation() != null) {
                JsonNode node = objectMapper.readTree(order.getLocation());
                customerLat = node.path("latitude").asText(null);
                customerLng = node.path("longitude").asText(null);
            }
        } catch (Exception e) {
            log.warn("Could not parse stored location JSON for order {}: {}", order.getId(), e.getMessage());
        }
        BigDecimal distanceKm = restaurant != null ? orderPricingService.distanceKm(restaurant, customerLat, customerLng) : BigDecimal.ZERO;
        BigDecimal commissionBase = commissionBasis == CommissionBasis.DELIVERY_CHARGE_ONLY ? order.getDeliveryCharge() : order.getTotal();
        BigDecimal payoutEstimate = commissionBase != null && rider.getCommissionRate() != null
                ? commissionBase.multiply(rider.getCommissionRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        User customer = userRepository.findById(order.getUserId().longValue()).orElse(null);
        List<DeliveryAssignmentResponse.Item> items = orderItemRepository.findByOrderId(order.getId().intValue()).stream()
                .map(i -> new DeliveryAssignmentResponse.Item(i.getName(), i.getQuantity() != null ? i.getQuantity() : 1))
                .toList();

        LocalDateTime acceptedAt = null;
        LocalDateTime pickedUpAt = null;
        LocalDateTime deliveredAt = null;
        String assignedBy = "DELIVERY";
        for (var entry : orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())) {
            if (OrderStatusCode.RIDER_ASSIGNED.name().equals(entry.getToStatus())) {
                acceptedAt = entry.getCreatedAt();
                assignedBy = entry.getActorType() != null ? entry.getActorType() : assignedBy;
            } else if (OrderStatusCode.PICKED_UP.name().equals(entry.getToStatus())) {
                pickedUpAt = entry.getCreatedAt();
            } else if (OrderStatusCode.DELIVERED.name().equals(entry.getToStatus())) {
                deliveredAt = entry.getCreatedAt();
            }
        }

        OrderStatusCode status = orderStatusService.codeFor(order.getOrderstatusId());
        return new DeliveryAssignmentResponse(
                order.getId(), order.getUniqueOrderId(), status != null ? status.name() : "UNKNOWN", assignedBy,
                order.getRestaurantId().longValue(),
                restaurant != null ? restaurant.getName() : "Unknown",
                restaurant != null ? restaurant.getAddress() : "",
                restaurant != null ? parseCoordinate(restaurant.getLatitude()) : BigDecimal.ZERO,
                restaurant != null ? parseCoordinate(restaurant.getLongitude()) : BigDecimal.ZERO,
                restaurant != null ? restaurant.getContactNumber() : null,
                customer != null ? customer.getName() : "Customer",
                order.getAddress(),
                parseCoordinate(customerLat), parseCoordinate(customerLng),
                customer != null ? customer.getPhone() : null,
                items, order.getPayable(), order.getPaymentMode(), payoutEstimate, distanceKm,
                order.getCreatedAt(), acceptedAt, pickedUpAt, deliveredAt);
    }

    private void recordCashCollection(Long riderUserId, BigDecimal amount, String uniqueOrderId) {
        DeliveryCollection collection = deliveryCollectionRepository.findByUserId(riderUserId.intValue())
                .orElseGet(() -> {
                    DeliveryCollection c = new DeliveryCollection();
                    c.setUserId(riderUserId.intValue());
                    c.setAmount(BigDecimal.ZERO);
                    c.setCreatedAt(LocalDateTime.now());
                    return c;
                });
        collection.setAmount(collection.getAmount().add(amount));
        collection.setUpdatedAt(LocalDateTime.now());
        collection = deliveryCollectionRepository.save(collection);

        DeliveryCollectionLog collectionLog = new DeliveryCollectionLog();
        collectionLog.setDeliveryCollectionId(collection.getId().intValue());
        collectionLog.setAmount(amount);
        collectionLog.setType("COD");
        collectionLog.setMessage("Cash collected for order #" + uniqueOrderId);
        collectionLog.setCreatedAt(LocalDateTime.now());
        collectionLog.setUpdatedAt(LocalDateTime.now());
        deliveryCollectionLogRepository.save(collectionLog);
    }

    private Order ownedByRider(Long riderUserId, Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        AcceptDelivery accept = acceptDeliveryRepository.findByOrderId(order.getId().intValue())
                .orElseThrow(() -> {
                    log.warn("Rejected rider action on order {}: not yet assigned to any rider", orderId);
                    return new ForbiddenException("This order has not been assigned to a rider");
                });
        if (!accept.getUserId().equals(riderUserId.intValue())) {
            log.warn("Rider {} attempted an action on order {} which is assigned to a different rider", riderUserId, orderId);
            throw new ForbiddenException("This order is not assigned to you");
        }
        return order;
    }

    /**
     * The extra {@code data} riding alongside a silent "Rider assigned" push (see
     * OrderNotificationService#notify(role, userId, title, body, data)) - lets the customer's
     * tracking page/home banner show who's coming without a follow-up fetch. {@code riderPhone}
     * comes from {@link User}, everything else from the already-loaded {@link DeliveryGuyDetail} -
     * both null-safe since a profile can be created without every optional field filled in yet.
     */
    private Map<String, Object> riderAssignedData(Long orderId, DeliveryGuyDetail rider, Long riderUserId) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("status", OrderStatusCode.RIDER_ASSIGNED.name());
        data.put("riderId", riderUserId);
        if (rider.getName() != null) data.put("riderName", rider.getName());
        if (rider.getPhoto() != null) data.put("riderPhoto", rider.getPhoto());
        if (rider.getVehicleNumber() != null) data.put("riderVehicleNumber", rider.getVehicleNumber());
        if (rider.getRating() != null) data.put("riderRating", rider.getRating());
        userRepository.findById(riderUserId).map(User::getPhone).ifPresent(phone -> data.put("riderPhone", phone));
        return data;
    }

    private DeliveryGuyDetail riderProfile(Long riderUserId) {
        User user = userRepository.findById(riderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + riderUserId));
        if (user.getDeliveryGuyDetailId() == null) {
            log.warn("User {} attempted a rider action without a rider profile", riderUserId);
            throw new ForbiddenException("You do not have a rider profile");
        }
        return deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found"));
    }
}
