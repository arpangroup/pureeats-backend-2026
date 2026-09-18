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

    @Value("${pureeats.commission.basis:FULL_ORDER}")
    private CommissionBasis commissionBasis;

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
        return orderRepository.findByOrderstatusIdInOrderByCreatedAtDesc(statusIds).stream()
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
        long activeCount = acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue()).size();
        if (activeCount >= rider.getMaxAcceptDeliveryLimit()) {
            log.warn("Rejected delivery acceptance for rider {}: at concurrent delivery limit ({}/{})",
                    riderUserId, activeCount, rider.getMaxAcceptDeliveryLimit());
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
        rider.setIsOnline(isOnline);
        deliveryGuyDetailRepository.save(rider);
        log.info("Rider {} is now {}", riderUserId, isOnline ? "ONLINE" : "OFFLINE");
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

    /** The signed-in rider's own delivery history, mirroring {@code OrderController#myOrders}'s "my orders" shape but sourced from {@link AcceptDeliveryRepository} (assignments) rather than {@code OrderRepository#findByUserId} (which is the customer's own orders). */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> myOrders(Long riderUserId) {
        return acceptDeliveryRepository.findByUserIdOrderByIdDesc(riderUserId.intValue()).stream()
                .map(accept -> orderService.findOrThrow(accept.getOrderId().longValue()))
                .map(orderService::toSummary)
                .toList();
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
