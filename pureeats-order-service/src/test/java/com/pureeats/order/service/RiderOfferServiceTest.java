package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.catalog.service.SettingSchemaService;
import com.pureeats.catalog.service.SettingValueService;
import com.pureeats.domain.entity.AcceptDelivery;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.RiderOrderDismissalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiderOfferServiceTest {

    @Mock private OrderNotificationService notifications;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AcceptDeliveryRepository acceptDeliveryRepository;
    @Mock private OrderStatusService orderStatusService;
    @Mock private SettingValueService settings;
    @Mock private RiderOrderDismissalRepository dismissals;
    @Mock private com.pureeats.order.repository.RiderOrderOfferRepository offers;
    @Mock private com.pureeats.order.repository.RiderOfferRunRepository runs;
    @Mock private com.pureeats.user.repository.UserRepository users;
    @Mock private RiderDispatchService dispatch;
    @InjectMocks private RiderOfferService service;

    private Order order;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "availableOrderWindowHours", 2L);
        order = new Order();
        order.setId(7L);
        order.setUniqueOrderId("PE-7");
        order.setRestaurantId(3);
        order.setDeliveryType(0);
        order.setPayable(new BigDecimal("250"));
        restaurant = new Restaurant();
        restaurant.setId(3L);
        restaurant.setName("Test Kitchen");
        lenient().when(settings.getBoolean(eq(SettingSchemaService.RIDER_REOFFER_ENABLED), anyBoolean())).thenReturn(true);
        lenient().when(settings.getInt(eq(SettingSchemaService.RIDER_REOFFER_INTERVAL_MINUTES), anyInt())).thenReturn(2);
        lenient().when(settings.getInt(eq(SettingSchemaService.RIDER_REOFFER_MAX_OFFERS), anyInt())).thenReturn(3);
        lenient().when(settings.getString(eq(SettingSchemaService.RIDER_REOFFER_MODE), any())).thenReturn("LIMITED");
        lenient().when(orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(order));
        lenient().when(acceptDeliveryRepository.findByOrderId(7)).thenReturn(Optional.empty());
        lenient().when(restaurantRepository.findById(3L)).thenReturn(Optional.of(restaurant));
    }

    @Test
    void offer_countsTheRound_sendsIt_andRecordsWhoWasAlerted() {
        when(notifications.notifyDeliveryPartnersOfAvailableOrder(any(), any(), any(), anyInt())).thenReturn(List.of(11L, 12L));
        assertEquals(2, service.offer(order, restaurant));
        verify(offers, times(2)).save(any());
        service.offer(order, restaurant);

        assertEquals(2, order.getRiderOfferCount());
        verify(notifications).notifyDeliveryPartnersOfAvailableOrder(7L, restaurant, new BigDecimal("250"), 1);
        verify(notifications).notifyDeliveryPartnersOfAvailableOrder(7L, restaurant, new BigDecimal("250"), 2);
    }

    @Test
    void anUnassignedOrder_isOfferedAgainAfterTheInterval_andTheRunIsRecorded() {
        order.setRiderOfferCount(1);
        order.setLastRiderOfferAt(LocalDateTime.now().minusMinutes(3));

        service.reofferUnassigned();

        verify(notifications).notifyDeliveryPartnersOfAvailableOrder(7L, restaurant, new BigDecimal("250"), 2);
        var run = org.mockito.ArgumentCaptor.forClass(com.pureeats.order.entity.RiderOfferRun.class);
        verify(runs).save(run.capture());
        assertEquals(1, run.getValue().getReoffered());
        assertEquals("PE-7", run.getValue().getReofferedOrders());
    }

    @Test
    void aDismissal_countsForTheRoundItWasMadeIn() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        order.setRiderOfferCount(2);
        service.dismiss(42L, 7L);
        var saved = org.mockito.ArgumentCaptor.forClass(com.pureeats.order.entity.RiderOrderDismissal.class);
        verify(dismissals).save(saved.capture());
        assertEquals(2, saved.getValue().getOfferRound());
    }

    @Test
    void summary_saysWhenTheNextAlertIsDue() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderStatusService.codeFor(any())).thenReturn(com.pureeats.domain.enums.OrderStatusCode.RESTAURANT_ACCEPTED);
        LocalDateTime last = LocalDateTime.now().minusSeconds(30);
        order.setRiderOfferCount(1);
        order.setLastRiderOfferAt(last);

        var summary = service.summary(7L);

        assertEquals("WAITING", summary.state());
        assertEquals(last.plusMinutes(2), summary.nextOfferAt());
    }

    @Test
    void notBeforeTheInterval() {
        order.setRiderOfferCount(1);
        order.setLastRiderOfferAt(LocalDateTime.now().minusSeconds(30));

        service.reofferUnassigned();

        verify(notifications, never()).notifyDeliveryPartnersOfAvailableOrder(any(), any(), any(), anyInt());
    }

    @Test
    void anAssignedOrder_isNotOfferedAgain() {
        order.setRiderOfferCount(1);
        order.setLastRiderOfferAt(LocalDateTime.now().minusMinutes(10));
        when(acceptDeliveryRepository.findByOrderId(7)).thenReturn(Optional.of(new AcceptDelivery()));

        service.reofferUnassigned();

        verify(notifications, never()).notifyDeliveryPartnersOfAvailableOrder(any(), any(), any(), anyInt());
    }

    @Test
    void afterTheMaximum_adminsAreAlertedOnce_andNoMoreOffers() {
        order.setRiderOfferCount(3);
        order.setLastRiderOfferAt(LocalDateTime.now().minusMinutes(5));

        service.reofferUnassigned();
        service.reofferUnassigned();

        verify(notifications, never()).notifyDeliveryPartnersOfAvailableOrder(any(), any(), any(), anyInt());
        verify(notifications, times(1)).notifyAdminsOfNewOrder(eq("No delivery partner yet"), contains("PE-7"), anyMap());
    }

    @Test
    void untilAssignedMode_keepsOfferingPastTheMaximum() {
        when(settings.getString(eq(SettingSchemaService.RIDER_REOFFER_MODE), any())).thenReturn("UNTIL_ASSIGNED");
        order.setRiderOfferCount(9);
        order.setLastRiderOfferAt(LocalDateTime.now().minusMinutes(5));

        service.reofferUnassigned();

        verify(notifications).notifyDeliveryPartnersOfAvailableOrder(7L, restaurant, new BigDecimal("250"), 10);
    }

    @Test
    void switchedOff_offersNothing_butTheRunIsStillRecorded() {
        when(settings.getBoolean(eq(SettingSchemaService.RIDER_REOFFER_ENABLED), anyBoolean())).thenReturn(false);

        service.reofferUnassigned();

        verifyNoInteractions(notifications);
        verify(runs).save(any());
    }
}
