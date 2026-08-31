package com.pureeats.order.service;

import com.pureeats.domain.entity.OrderStatus;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderStatusResponse;
import com.pureeats.order.repository.OrderStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps the clean {@link OrderStatusCode} enum onto the legacy {@code order_statuses} lookup table, seeding rows on demand. */
@Service
@RequiredArgsConstructor
@Slf4j
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
                    log.info("Seeding new order-status lookup row for {}", code);
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
    public List<OrderStatusResponse> listAll() {
        return orderStatusRepository.findAll().stream()
                .map(s -> new OrderStatusResponse(s.getId(), s.getName())).toList();
    }

    @Transactional(readOnly = true)
    public OrderStatusCode codeFor(Integer id) {
        OrderStatusCode cached = idToCode.get(id);
        if (cached != null) {
            return cached;
        }
        OrderStatusCode resolved = orderStatusRepository.findById(id.longValue())
                .map(s -> OrderStatusCode.valueOf(s.getName()))
                .orElse(null);
        if (resolved == null) {
            log.warn("No order status found for lookup id {}", id);
        }
        return resolved;
    }
}
