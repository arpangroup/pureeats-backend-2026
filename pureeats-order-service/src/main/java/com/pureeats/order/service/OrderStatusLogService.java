package com.pureeats.order.service;

import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderStatusLogResponse;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.OrderStatusLogRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    private String actorName(Long actorUserId) {
        if (actorUserId == null) {
            return null;
        }
        return userRepository.findById(actorUserId).map(User::getName).orElse(null);
    }
}
