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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
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
                    return new OrderSummaryResponse(o.getId(), o.getUniqueOrderId(), status.name(),
                            o.getRestaurantId().longValue(), o.getPayable(), o.getCreatedAt());
                }).toList();
    }

    @Transactional
    public OrderResponse acceptToDeliver(Long riderUserId, Long orderId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        Order order = orderService.findOrThrow(orderId);
        if (acceptDeliveryRepository.findByOrderId(order.getId().intValue()).isPresent()) {
            throw new BadRequestException("This order has already been assigned to a rider");
        }
        long activeCount = acceptDeliveryRepository.findByUserIdAndIsCompleteFalse(riderUserId.intValue()).size();
        if (activeCount >= rider.getMaxAcceptDeliveryLimit()) {
            throw new BadRequestException("You have reached your maximum concurrent delivery limit");
        }

        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(order.getId().intValue());
        accept.setUserId(riderUserId.intValue());
        accept.setCustomerId(order.getUserId());
        accept.setIsComplete(false);
        acceptDeliveryRepository.save(accept);

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.RIDER_ASSIGNED));
        order.setRiderAcceptAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Rider assigned",
                "A delivery partner has been assigned to order #" + order.getUniqueOrderId());
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse pickedUp(Long riderUserId, Long orderId) {
        Order order = ownedByRider(riderUserId, orderId);
        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.PICKED_UP));
        order.setRiderPickedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse deliver(Long riderUserId, Long orderId, String deliveryPin) {
        Order order = ownedByRider(riderUserId, orderId);
        if (!order.getDeliveryPin().equalsIgnoreCase(deliveryPin)) {
            throw new BadRequestException("Incorrect delivery PIN");
        }

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.DELIVERED));
        order.setRiderDeliverAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        acceptDeliveryRepository.findByOrderId(order.getId().intValue()).ifPresent(a -> {
            a.setIsComplete(true);
            acceptDeliveryRepository.save(a);
        });

        DeliveryGuyDetail rider = riderProfile(riderUserId);
        BigDecimal commissionBase = commissionBasis == CommissionBasis.DELIVERY_CHARGE_ONLY
                ? order.getDeliveryCharge() : order.getTotal();
        BigDecimal riderEarning = commissionBase.multiply(rider.getCommissionRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        walletService.credit(riderUserId, riderEarning, "Delivery earning for order #" + order.getUniqueOrderId());

        BigDecimal restaurantEarning = order.getTotal().subtract(order.getRestaurantCharge());
        restaurantPayoutService.recordEarning(order.getRestaurantId(), restaurantEarning);

        BigDecimal cashCollected = BigDecimal.ZERO;
        if ("COD".equals(order.getPaymentMode())) {
            cashCollected = order.getPayable();
            recordCashCollection(riderUserId, cashCollected, order.getUniqueOrderId());
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

        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Order delivered",
                "Your order #" + order.getUniqueOrderId() + " has been delivered. Enjoy your meal!");
        return orderService.toResponse(order);
    }

    @Transactional
    public void recordGpsPing(GpsPingRequest request) {
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
                .orElseThrow(() -> new ResourceNotFoundException("No GPS ping recorded for this order yet"));
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

        DeliveryCollectionLog log = new DeliveryCollectionLog();
        log.setDeliveryCollectionId(collection.getId().intValue());
        log.setAmount(amount);
        log.setType("COD");
        log.setMessage("Cash collected for order #" + uniqueOrderId);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        deliveryCollectionLogRepository.save(log);
    }

    private Order ownedByRider(Long riderUserId, Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        AcceptDelivery accept = acceptDeliveryRepository.findByOrderId(order.getId().intValue())
                .orElseThrow(() -> new ForbiddenException("This order has not been assigned to a rider"));
        if (!accept.getUserId().equals(riderUserId.intValue())) {
            throw new ForbiddenException("This order is not assigned to you");
        }
        return order;
    }

    private DeliveryGuyDetail riderProfile(Long riderUserId) {
        User user = userRepository.findById(riderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + riderUserId));
        if (user.getDeliveryGuyDetailId() == null) {
            throw new ForbiddenException("You do not have a rider profile");
        }
        return deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Rider profile not found"));
    }
}
