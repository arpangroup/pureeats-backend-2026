package com.pureeats.order.service;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderStatusLogResponse;
import com.pureeats.order.dto.OrderTimelineResponse;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderStatusLogRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Records and lists an order's status-transition history - the "journey" the order detail page shows. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusLogService {

    private final OrderStatusLogRepository orderStatusLogRepository;
    private final UserRepository userRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;

    /**
     * Transitions that end an order WITHOUT a delivery - the assigned rider (if any) is free again.
     * DELIVERED is deliberately absent: DeliveryOrderService#completeDelivery closes that assignment
     * itself, together with crediting the rider.
     */
    private static final java.util.Set<OrderStatusCode> RELEASES_RIDER = java.util.EnumSet.of(
            OrderStatusCode.CANCELLED, OrderStatusCode.REJECTED, OrderStatusCode.RETURNED,
            OrderStatusCode.AUTO_CANCELLED, OrderStatusCode.SELF_PICKUP_COMPLETED);

    /**
     * A partner assigned before the food is ready leaves the status unchanged (see DeliveryOrderService#recordAssignment),
     * so that log entry is recognised by this note suffix instead of a RIDER_ASSIGNED status.
     */
    public static final String ASSIGNED_WHILE_PREPARING_SUFFIX = " - waiting for the food to be ready";

    static boolean isAssignedWhilePreparing(OrderStatusLog entry) {
        return entry.getNote() != null && entry.getNote().endsWith(ASSIGNED_WHILE_PREPARING_SUFFIX);
    }

    @Transactional
    public void record(Long orderId, OrderStatusCode from, OrderStatusCode to, String actorType, Long actorUserId, String note) {
        log.debug("Recording status log for order {}: {} -> {} by {} {}", orderId, from, to, actorType, actorUserId);
        OrderStatusLog entry = new OrderStatusLog();
        entry.setOrderId(orderId);
        entry.setFromStatus(from != null ? from.name() : null);
        entry.setToStatus(to.name());
        entry.setActorType(actorType);
        entry.setActorUserId(actorUserId);
        entry.setNote(note);
        entry.setCreatedAt(LocalDateTime.now());
        orderStatusLogRepository.save(entry);
        releaseRiderIfEnded(orderId, to);
    }

    /**
     * Every status transition (customer cancel, store-owner reject/cancel, admin status override, ...)
     * is recorded through {@link #record}, which makes this the one place to free the rider when an
     * order ends without being delivered. Previously the AcceptDelivery row was only ever closed on
     * delivery, so a cancelled assignment held one of the rider's concurrent-delivery slots forever.
     */
    private void releaseRiderIfEnded(Long orderId, OrderStatusCode to) {
        if (!RELEASES_RIDER.contains(to)) return;
        acceptDeliveryRepository.findByOrderId(orderId.intValue())
                .filter(accept -> !Boolean.TRUE.equals(accept.getIsComplete()))
                .ifPresent(accept -> {
                    accept.setIsComplete(true);
                    acceptDeliveryRepository.save(accept);
                    log.info("Released rider {} from order {} ({})", accept.getUserId(), orderId, to);
                });
    }

    @Transactional(readOnly = true)
    public List<OrderStatusLogResponse> journey(Long orderId) {
        return orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(entry -> new OrderStatusLogResponse(entry.getId(), label(entry.getFromStatus()), label(entry.getToStatus()),
                        entry.getActorType(), entry.getActorUserId(), actorName(entry.getActorUserId()), entry.getNote(), entry.getCreatedAt()))
                .toList();
    }

    /** Log rows store the raw {@link OrderStatusCode} name - shown to the admin as its friendlier {@link OrderStatusCode#label()} instead. */
    private String label(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        try {
            return OrderStatusCode.valueOf(rawStatus).label();
        } catch (IllegalArgumentException e) {
            return rawStatus;
        }
    }

    /**
     * The compact "when did each milestone happen" view, derived from the same journey log rather
     * than a separately maintained set of columns - the first log entry reaching each status,
     * or null if the order never got there. {@code placedAt} is the very first entry regardless of
     * its {@code toStatus} (PLACED, or RESTAURANT_ACCEPTED for an auto-accepting restaurant).
     */
    @Transactional(readOnly = true)
    public OrderTimelineResponse timeline(Long orderId) {
        List<OrderStatusLog> entries = orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (entries.isEmpty()) {
            log.debug("No status log entries found for order {}, returning empty timeline", orderId);
            return new OrderTimelineResponse(null, null, null, null, null, null, null, null);
        }
        Map<String, LocalDateTime> firstSeenAt = new java.util.HashMap<>();
        LocalDateTime riderAssignedAt = null;
        LocalDateTime readyAt = null;
        for (OrderStatusLog entry : entries) {
            firstSeenAt.putIfAbsent(entry.getToStatus(), entry.getCreatedAt());
            // Food marked ready with a partner already assigned goes straight to RIDER_ASSIGNED (StoreOwnerOrderService#ready),
            // so READY_FOR_PICKUP never appears - the store's step to RIDER_ASSIGNED is when the food was ready.
            if (readyAt == null && (OrderStatusCode.READY_FOR_PICKUP.name().equals(entry.getToStatus())
                    || ("STORE_OWNER".equals(entry.getActorType()) && OrderStatusCode.RIDER_ASSIGNED.name().equals(entry.getToStatus())))) {
                readyAt = entry.getCreatedAt();
            }
            // Assigned while the kitchen was still preparing: the status didn't change, but the partner was assigned.
            if (riderAssignedAt == null && (OrderStatusCode.RIDER_ASSIGNED.name().equals(entry.getToStatus()) || isAssignedWhilePreparing(entry))) {
                riderAssignedAt = entry.getCreatedAt();
            }
        }
        LocalDateTime placedAt = entries.get(0).getCreatedAt();
        return new OrderTimelineResponse(
                placedAt,
                firstSeenAt.get(OrderStatusCode.RESTAURANT_ACCEPTED.name()),
                readyAt,
                riderAssignedAt,
                firstSeenAt.get(OrderStatusCode.PICKED_UP.name()),
                firstSeenAt.get(OrderStatusCode.DELIVERED.name()),
                firstSeenAt.get(OrderStatusCode.SELF_PICKUP_COMPLETED.name()),
                firstSeenAt.get(OrderStatusCode.CANCELLED.name()));
    }

    private String actorName(Long actorUserId) {
        if (actorUserId == null) {
            return null;
        }
        return userRepository.findById(actorUserId).map(User::getName).orElse(null);
    }
}
