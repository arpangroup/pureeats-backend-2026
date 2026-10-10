package com.pureeats.order.service;

import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.entity.OrderStatusLog;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderStatusLogRepository;
import com.pureeats.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusLogServiceTimelineTest {

    @Mock private OrderStatusLogRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private AcceptDeliveryRepository acceptDeliveryRepository;
    @InjectMocks private OrderStatusLogService service;

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 11, 12, 0);

    private static OrderStatusLog entry(OrderStatusCode from, OrderStatusCode to, String note, int minute) {
        OrderStatusLog e = new OrderStatusLog();
        e.setFromStatus(from != null ? from.name() : null);
        e.setToStatus(to.name());
        e.setNote(note);
        e.setCreatedAt(T0.plusMinutes(minute));
        return e;
    }

    @Test
    void partnerAssignedWhileTheKitchenIsPreparing_showsAsRiderAssigned() {
        when(repository.findByOrderIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(
                entry(null, OrderStatusCode.PLACED, null, 0),
                entry(OrderStatusCode.PLACED, OrderStatusCode.RESTAURANT_ACCEPTED, null, 1),
                // the status stays RESTAURANT_ACCEPTED - only the note says a partner was assigned
                entry(OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.RESTAURANT_ACCEPTED,
                        "Delivery partner assigned" + OrderStatusLogService.ASSIGNED_WHILE_PREPARING_SUFFIX, 3),
                entry(OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.READY_FOR_PICKUP, null, 20)));

        var timeline = service.timeline(5L);

        assertEquals(T0.plusMinutes(3), timeline.riderAssignedAt());
        assertEquals(T0.plusMinutes(20), timeline.restaurantReadyAt());
    }

    @Test
    void foodMarkedReadyWithAPartnerAlreadyAssigned_countsAsReadyForPickup() {
        OrderStatusLog ready = entry(OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.RIDER_ASSIGNED, null, 18);
        ready.setActorType("STORE_OWNER");
        when(repository.findByOrderIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(
                entry(null, OrderStatusCode.PLACED, null, 0),
                entry(OrderStatusCode.RESTAURANT_ACCEPTED, OrderStatusCode.RESTAURANT_ACCEPTED,
                        "Delivery partner assigned" + OrderStatusLogService.ASSIGNED_WHILE_PREPARING_SUFFIX, 3),
                ready));

        var timeline = service.timeline(5L);

        assertEquals(T0.plusMinutes(18), timeline.restaurantReadyAt());
        assertEquals(T0.plusMinutes(3), timeline.riderAssignedAt());
    }

    @Test
    void partnerAssignedAfterTheFoodIsReady_stillUsesTheRiderAssignedStatus() {
        when(repository.findByOrderIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(
                entry(null, OrderStatusCode.PLACED, null, 0),
                entry(OrderStatusCode.READY_FOR_PICKUP, OrderStatusCode.RIDER_ASSIGNED, null, 25)));

        assertEquals(T0.plusMinutes(25), service.timeline(5L).riderAssignedAt());
    }
}
