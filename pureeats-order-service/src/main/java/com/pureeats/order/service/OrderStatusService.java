package com.pureeats.order.service;

import com.pureeats.domain.entity.OrderStatus;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.repository.OrderStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps the clean {@link OrderStatusCode} enum onto the legacy {@code order_statuses} lookup table, seeding rows on demand. */
@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private final OrderStatusRepository orderStatusRepository;
    private final Map<OrderStatusCode, Integer> codeToId = new EnumMap<>(OrderStatusCode.class);
    private final Map<Integer, OrderStatusCode> idToCode = new ConcurrentHashMap<>();

    @Transactional
    public synchronized Integer idFor(OrderStatusCode code) {
        Integer cached = codeToId.get(code);
        if (cached != null) {
            return cached;
        }
        OrderStatus status = orderStatusRepository.findByName(code.name())
                .orElseGet(() -> {
                    OrderStatus s = new OrderStatus();
                    s.setName(code.name());
                    return orderStatusRepository.save(s);
                });
        Integer id = status.getId().intValue();
        codeToId.put(code, id);
        idToCode.put(id, code);
        return id;
    }

    @Transactional(readOnly = true)
    public OrderStatusCode codeFor(Integer id) {
        OrderStatusCode cached = idToCode.get(id);
        if (cached != null) {
            return cached;
        }
        return orderStatusRepository.findById(id.longValue())
                .map(s -> OrderStatusCode.valueOf(s.getName()))
                .orElse(null);
    }
}
