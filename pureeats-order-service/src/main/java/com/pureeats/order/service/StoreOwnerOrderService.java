package com.pureeats.order.service;

import com.pureeats.catalog.service.RestaurantService;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.notification.service.NotificationDispatchService;
import com.pureeats.order.dto.OrderResponse;
import com.pureeats.order.dto.OrderSummaryResponse;
import com.pureeats.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private final NotificationDispatchService notificationDispatchService;
    private final OrderStatusLogService orderStatusLogService;

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
        order.setPrepareTime(DEFAULT_PREPARE_TIME_MINUTES);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, "STORE_OWNER", ownerUserId, null);
        log.info("Order {} transitioned PLACED -> RESTAURANT_ACCEPTED by store owner {}", orderId, ownerUserId);

        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Order accepted",
                "Your order #" + order.getUniqueOrderId() + " has been accepted by the restaurant");
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse markReady(Long ownerUserId, Long orderId) {
        log.info("Store owner {} marking order {} ready for pickup", ownerUserId, orderId);
        Order order = ownedOrder(ownerUserId, orderId);
        requireStatus(order, OrderStatusCode.RESTAURANT_ACCEPTED);

        order.setOrderstatusId(orderStatusService.idFor(OrderStatusCode.READY_FOR_PICKUP));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderStatusLogService.record(order.getId(), OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.READY_FOR_PICKUP, "STORE_OWNER", ownerUserId, null);
        log.info("Order {} transitioned RESTAURANT_ACCEPTED -> READY_FOR_PICKUP by store owner {}", orderId, ownerUserId);
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

        BigDecimal restaurantEarning = order.getTotal().subtract(order.getRestaurantCharge());
        restaurantPayoutService.recordEarning(order.getRestaurantId(), restaurantEarning);
        return orderService.toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(Long ownerUserId, Long orderId) {
        log.info("Store owner {} cancelling order {}", ownerUserId, orderId);
        Order order = ownedOrder(ownerUserId, orderId);
        OrderStatusCode current = orderStatusService.codeFor(order.getOrderstatusId());
        if (current == OrderStatusCode.DELIVERED || current == OrderStatusCode.CANCELLED
                || current == OrderStatusCode.SELF_PICKUP_COMPLETED) {
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
        notificationDispatchService.notifyUser(order.getUserId().longValue(), "Order cancelled",
                "Your order #" + order.getUniqueOrderId() + " was cancelled by the restaurant");
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
            return new OrderSummaryResponse(o.getId(), o.getUniqueOrderId(), status != null ? status.name() : "UNKNOWN",
                    o.getRestaurantId().longValue(), null, null, o.getPayable(), o.getCreatedAt(), null);
        }).toList();
    }
}
