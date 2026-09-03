package com.pureeats.order.service;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderStatusLogResponse;
import com.pureeats.order.dto.OrderTimelineResponse;
import com.pureeats.order.entity.OrderStatusLog;
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
        for (OrderStatusLog entry : entries) {
            firstSeenAt.putIfAbsent(entry.getToStatus(), entry.getCreatedAt());
        }
        LocalDateTime placedAt = entries.get(0).getCreatedAt();
        return new OrderTimelineResponse(
                placedAt,
                firstSeenAt.get(OrderStatusCode.RESTAURANT_ACCEPTED.name()),
                firstSeenAt.get(OrderStatusCode.READY_FOR_PICKUP.name()),
                firstSeenAt.get(OrderStatusCode.RIDER_ASSIGNED.name()),
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
