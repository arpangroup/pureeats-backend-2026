package com.pureeats.order.service;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderStatusLogResponse;
import com.pureeats.order.dto.OrderTimelineResponse;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.OrderStatusLogRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Records and lists an order's status-transition history - the "journey" the order detail page shows. */
@Service
@RequiredArgsConstructor
public class OrderStatusLogService {

    private final OrderStatusLogRepository orderStatusLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long orderId, OrderStatusCode from, OrderStatusCode to, String actorType, Long actorUserId, String note) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(from != null ? from.name() : null);
        log.setToStatus(to.name());
        log.setActorType(actorType);
        log.setActorUserId(actorUserId);
        log.setNote(note);
        log.setCreatedAt(LocalDateTime.now());
        orderStatusLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusLogResponse> journey(Long orderId) {
        return orderStatusLogRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(log -> new OrderStatusLogResponse(log.getId(), log.getFromStatus(), log.getToStatus(),
                        log.getActorType(), log.getActorUserId(), actorName(log.getActorUserId()), log.getNote(), log.getCreatedAt()))
                .toList();
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
