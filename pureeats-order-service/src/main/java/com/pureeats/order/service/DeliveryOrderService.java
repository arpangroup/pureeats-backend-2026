package com.pureeats.order.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.notification.service.NotificationDispatchService;
import com.pureeats.order.dto.*;
import com.pureeats.order.repository.*;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

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
    private final NotificationDispatchService notificationDispatchService;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final OrderStatusLogService orderStatusLogService;

    @Value("${pureeats.commission.basis:FULL_ORDER}")
    private CommissionBasis commissionBasis;

    @Transactional
    public List<OrderSummaryResponse> availableOrders() {
        List<Integer> statusIds = List.of(
                orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED),
                orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP));
        return orderRepository.findByOrderstatusIdInOrderByCreatedAtDesc(statusIds).stream()
                .filter(o -> o.getDeliveryType() == 0 && acceptDeliveryRepository.findByOrderId(o.getId().intValue()).isEmpty())
                .map(o -> {
                    OrderStatusCode status = orderStatusService.codeFor(o.getOrderstatusId());
                    return new OrderSummaryResponse(o.getId(), o.getUniqueOrderId(), status.label(),
                            o.getRestaurantId().longValue(), null, null, o.getPayable(), o.getCreatedAt(), null);
                }).toList();
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

        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId(), "ORDER_UPDATE");
        return orderService.toResponse(order);
    }

    /** Admin override - assigns a specific rider to a specific order directly, skipping the rider's own concurrent-delivery-limit check (an explicit admin decision, not a rider self-service action). */
    @Transactional
    public OrderResponse assignDriverAsAdmin(Long adminUserId, Long orderId, Long riderUserId) {
        log.info("Admin {} assigning rider {} to order {}", adminUserId, riderUserId, orderId);
        riderProfile(riderUserId);
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

        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId(), "ORDER_UPDATE");
        notificationDispatchService.notifyUser(riderUserId, "New delivery assigned",
                "You've been assigned to deliver order #" + order.getUniqueOrderId(), "ORDER_UPDATE");
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

        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Order delivered",
                "Your order #" + order.getUniqueOrderId() + " has been delivered. Enjoy your meal!", "ORDER_UPDATE");
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
