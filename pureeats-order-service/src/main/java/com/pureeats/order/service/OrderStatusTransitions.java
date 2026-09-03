package com.pureeats.order.service;

import com.pureeats.domain.enums.OrderStatusCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for "what can this order legally become next" - shared by the
 * admin override endpoint (validates before applying) and the order-detail UI (only offers
 * meaningful next statuses, previous/illegal ones are never shown, let alone enabled).
 * <p>
 * Role-scoped endpoints (store-owner accept/ready, delivery pickup/deliver, customer cancel)
 * already enforce a stricter, single-transition precondition each and don't consult this table -
 * it exists for the admin override, which is intentionally more general.
 */
public final class OrderStatusTransitions {

    private static final Map<OrderStatusCode, Set<OrderStatusCode>> GRAPH = new EnumMap<>(OrderStatusCode.class);

    static {
        GRAPH.put(OrderStatusCode.PLACED, EnumSet.of(
                OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.CANCELLED,
                OrderStatusCode.REJECTED, OrderStatusCode.AUTO_CANCELLED));
        GRAPH.put(OrderStatusCode.RESTAURANT_ACCEPTED, EnumSet.of(OrderStatusCode.PREPARING, OrderStatusCode.CANCELLED));
        GRAPH.put(OrderStatusCode.PREPARING, EnumSet.of(OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.CANCELLED));
        GRAPH.put(OrderStatusCode.READY_FOR_PICKUP, EnumSet.of(
                OrderStatusCode.RIDER_ASSIGNED, OrderStatusCode.SELF_PICKUP_COMPLETED,
                OrderStatusCode.CANCELLED, OrderStatusCode.AUTO_CANCELLED));
        GRAPH.put(OrderStatusCode.RIDER_ASSIGNED, EnumSet.of(
                OrderStatusCode.PICKED_UP, OrderStatusCode.CANCELLED, OrderStatusCode.AUTO_CANCELLED));
        GRAPH.put(OrderStatusCode.PICKED_UP, EnumSet.of(OrderStatusCode.ON_THE_WAY, OrderStatusCode.RETURNED));
        GRAPH.put(OrderStatusCode.ON_THE_WAY, EnumSet.of(OrderStatusCode.DELIVERED, OrderStatusCode.RETURNED));
        GRAPH.put(OrderStatusCode.DELIVERED, EnumSet.noneOf(OrderStatusCode.class));
        GRAPH.put(OrderStatusCode.SELF_PICKUP_COMPLETED, EnumSet.noneOf(OrderStatusCode.class));
        GRAPH.put(OrderStatusCode.CANCELLED, EnumSet.noneOf(OrderStatusCode.class));
        GRAPH.put(OrderStatusCode.REJECTED, EnumSet.noneOf(OrderStatusCode.class));
        GRAPH.put(OrderStatusCode.RETURNED, EnumSet.noneOf(OrderStatusCode.class));
        GRAPH.put(OrderStatusCode.AUTO_CANCELLED, EnumSet.noneOf(OrderStatusCode.class));
    }

    private OrderStatusTransitions() {
    }

    public static Set<OrderStatusCode> legalNext(OrderStatusCode from) {
        return from == null ? EnumSet.noneOf(OrderStatusCode.class) : GRAPH.getOrDefault(from, EnumSet.noneOf(OrderStatusCode.class));
    }

    public static boolean isLegal(OrderStatusCode from, OrderStatusCode to) {
        return legalNext(from).contains(to);
    }
}
