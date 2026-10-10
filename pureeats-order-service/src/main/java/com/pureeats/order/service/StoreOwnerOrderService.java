package com.pureeats.order.service;

import com.pureeats.catalog.service.RestaurantService;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.AcceptDelivery;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.notification.enums.NotificationRecipientRole;
import com.pureeats.order.dto.OrderResponse;
import com.pureeats.order.dto.OrderSummaryResponse;
import com.pureeats.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreOwnerOrderService {

    private static final int DEFAULT_PREPARE_TIME_MINUTES = 20;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final RestaurantService restaurantService;
    private final RestaurantPayoutService restaurantPayoutService;
    private final WalletService walletService;
    private final OrderNotificationService orderNotificationService;
    private final OrderStatusLogService orderStatusLogService;
    private final OrderTimingService orderTimingService;
    private final com.pureeats.order.repository.AcceptDeliveryRepository acceptDeliveryRepository;

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> newOrders(Long ownerUserId, Long restaurantId) {
        restaurantService.assertOwnership(ownerUserId, restaurantId);
        return summarize(orderRepository.findByRestaurantIdAndOrderstatusIdOrderByCreatedAtDesc(
                restaurantId.intValue(), orderStatusService.idFor(OrderStatusCode.PLACED)));
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> runningOrders(Long ownerUserId, Long restaurantId) {
        restaurantService.assertOwnership(ownerUserId, restaurantId);
        List<Integer> runningStatusIds = List.of(
                orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED),
                orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP),
                orderStatusService.idFor(OrderStatusCode.RIDER_ASSIGNED),
                orderStatusService.idFor(OrderStatusCode.PICKED_UP));
        return summarize(orderRepository.findByOrderstatusIdInOrderByCreatedAtDesc(runningStatusIds).stream()
                .filter(o -> o.getRestaurantId().equals(restaurantId.intValue())).toList());
    }

    @Transactional
    public OrderResponse accept(Long ownerUserId, Long orderId) {
        log.info("Store owner {} accepting order {}", ownerUserId, orderId);
        Order order = ownedOrder(ownerUserId, orderId);
        requireStatus(order, OrderStatusCode.PLACED);

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.RESTAURANT_ACCEPTED));
        // T1 was set at placement (restaurant's preparation time); older orders get the default.
        if (order.getPrepareTime() == null || order.getPrepareTime() <= 0) order.setPrepareTime(DEFAULT_PREPARE_TIME_MINUTES);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, "STORE_OWNER", ownerUserId, null);
        log.info("Order {} transitioned PLACED -> RESTAURANT_ACCEPTED by store owner {}", orderId, ownerUserId);

        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Order accepted",
                "Your order #" + order.getUniqueOrderId() + " has been accepted by the restaurant",
                Map.of("orderId", order.getId(), "status", OrderStatusCode.RESTAURANT_ACCEPTED.name()));

        // The order is now visible in DeliveryOrderService#availableOrders() (RESTAURANT_ACCEPTED +
        // not self-pickup) - let online riders know immediately. Mirrors the equivalent broadcast in
        // OrderService#placeOrder for the auto-accept path, which this manual-accept path skips since
        // the order was still PLACED (not yet pickable) at placement time.
        if (order.getDeliveryType() == 0) {
            Restaurant restaurant = restaurantService.assertOwnership(ownerUserId, order.getRestaurantId().longValue());
            orderNotificationService.notifyDeliveryPartnersOfAvailableOrder(order.getId(), restaurant.getName(), order.getPayable());
        }
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse markReady(Long ownerUserId, Long orderId) {
        log.info("Store owner {} marking order {} ready for pickup", ownerUserId, orderId);
        Order order = ownedOrder(ownerUserId, orderId);
        OrderStatusCode from = orderStatusService.codeFor(order.getOrderstatusId());
        if (from != OrderStatusCode.PREPARING) {
            requireStatus(order, OrderStatusCode.RESTAURANT_ACCEPTED);
        }

        // A partner who accepted while the food was being prepared is already on it: go straight to RIDER_ASSIGNED.
        Optional<AcceptDelivery> assignment = acceptDeliveryRepository.findByOrderId(order.getId().intValue());
        OrderStatusCode to = assignment.isPresent() ? OrderStatusCode.RIDER_ASSIGNED : OrderStatusCode.READY_FOR_PICKUP;
        order.setOrderstatusId(orderStatusService.idFor(to));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), from, to, "STORE_OWNER", ownerUserId, assignment.isPresent() ? "Food ready for pickup" : null);
        log.info("Order {} transitioned {} -> {} by store owner {}", orderId, from, to, ownerUserId);
        assignment.ifPresent(a -> orderNotificationService.notify(NotificationRecipientRole.DELIVERY_PARTNER, a.getUserId().longValue(),
                "Order ready for pickup", "Order #" + order.getUniqueOrderId() + " is packed and ready - head to the counter.",
                Map.of("orderId", order.getId(), "status", OrderStatusCode.READY_FOR_PICKUP.name())));
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse markSelfPickupCompleted(Long ownerUserId, Long orderId) {
        log.info("Store owner {} marking order {} self-pickup completed", ownerUserId, orderId);
        Order order = ownedOrder(ownerUserId, orderId);
        requireStatus(order, OrderStatusCode.READY_FOR_PICKUP);

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.SELF_PICKUP_COMPLETED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.SELF_PICKUP_COMPLETED, "STORE_OWNER", ownerUserId, null);
        log.info("Order {} transitioned READY_FOR_PICKUP -> SELF_PICKUP_COMPLETED by store owner {}", orderId, ownerUserId);

        // item total − commission + packaging charge (see OrderService#restaurantPayoutFor).
        BigDecimal restaurantEarning = orderService.restaurantPayoutFor(order);
        restaurantPayoutService.recordEarning(order.getRestaurantId(), restaurantEarning);
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(Long ownerUserId, Long orderId) {
        log.info("Store owner {} cancelling order {}", ownerUserId, orderId);
        Order order = ownedOrder(ownerUserId, orderId);
        OrderStatusCode current = orderStatusService.codeFor(order.getOrderstatusId());
        if (current == OrderStatusCode.DELIVERED || current == OrderStatusCode.CANCELLED
                || current == OrderStatusCode.SELF_PICKUP_COMPLETED || current == OrderStatusCode.REJECTED
                || current == OrderStatusCode.RETURNED || current == OrderStatusCode.AUTO_CANCELLED) {
            log.warn("Rejected cancellation of order {} by store owner {}: already in terminal state {}", orderId, ownerUserId, current);
            throw new BadRequestException("This order can no longer be cancelled");
        }

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.CANCELLED));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        if ("WALLET".equals(order.getPaymentMode())) {
            walletService.credit(order.getUserId().longValue(), order.getPayable(),
                    "Refund for cancelled order #" + order.getUniqueOrderId());
        }
        orderStatusLogService.record(order.getId(), current, OrderStatusCode.CANCELLED, "STORE_OWNER", ownerUserId, null);
        log.info("Order {} transitioned {} -> CANCELLED by store owner {}", orderId, current, ownerUserId);
        orderNotificationService.notify(NotificationRecipientRole.CUSTOMER, order.getUserId().longValue(), "Order cancelled",
                "Your order #" + order.getUniqueOrderId() + " was cancelled by the restaurant",
                Map.of("orderId", order.getId(), "status", OrderStatusCode.CANCELLED.name()));
        return orderService.toResponse(order);
    }

    @Transactional(readOnly = true)
    public BigDecimal unsettledEarnings(Long ownerUserId, Long restaurantId) {
        restaurantService.assertOwnership(ownerUserId, restaurantId);
        return restaurantPayoutService.getUnsettledBalance(restaurantId.intValue());
    }

    @Transactional
    public void requestPayout(Long ownerUserId, Long restaurantId) {
        restaurantService.assertOwnership(ownerUserId, restaurantId);
        restaurantPayoutService.requestPayout(restaurantId.intValue());
    }

    private Order ownedOrder(Long ownerUserId, Long orderId) {
        Order order = orderService.findOrThrow(orderId);
        restaurantService.assertOwnership(ownerUserId, order.getRestaurantId().longValue());
        return order;
    }

    private void requireStatus(Order order, OrderStatusCode expected) {
        OrderStatusCode actual = orderStatusService.codeFor(order.getOrderstatusId());
        if (actual != expected) {
            log.warn("Rejected action on order {}: expected status {} but found {}", order.getId(), expected, actual);
            throw new BadRequestException("Order is not in the expected state for this action");
        }
    }

    private List<OrderSummaryResponse> summarize(List<Order> orders) {
        return orders.stream().map(o -> {
            OrderStatusCode status = orderStatusService.codeFor(o.getOrderstatusId());
            return new OrderSummaryResponse(o.getId(), o.getUniqueOrderId(), status != null ? status.label() : "UNKNOWN",
                    o.getRestaurantId().longValue(), null, null, o.getPayable(), o.getCreatedAt(), null, o.getOrderComment(),
                    o.getPrepareTime(), orderTimingService.prepDueAt(o));
        }).toList();
    }
}
