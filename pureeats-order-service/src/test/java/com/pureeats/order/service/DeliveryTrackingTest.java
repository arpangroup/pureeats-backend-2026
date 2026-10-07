package com.pureeats.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderTrackingResponse;
import com.pureeats.order.repository.*;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.DeliveryGuyLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Rider pings becoming an order's GPS trail, and the customer tracking view built from it. */
@ExtendWith(MockitoExtension.class)
class DeliveryTrackingTest {

    private static final long RIDER = 9L;
    private static final long CUSTOMER = 30L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private OrderStatusService orderStatusService;
    @Mock private AcceptDeliveryRepository acceptDeliveryRepository;
    @Mock private GpsTableRepository gpsTableRepository;
    @Mock private TripDetailRepository tripDetailRepository;
    @Mock private DeliveryCollectionRepository deliveryCollectionRepository;
    @Mock private DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    @Mock private RestaurantPayoutService restaurantPayoutService;
    @Mock private WalletService walletService;
    @Mock private OrderNotificationService orderNotificationService;
    @Mock private UserRepository userRepository;
    @Mock private DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    @Mock private OrderStatusLogService orderStatusLogService;
    @Mock private DeliveryGuyLocationService deliveryGuyLocationService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderPricingService orderPricingService;
    @Mock private OrderStatusLogRepository orderStatusLogRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private DeliveryOrderService service;

    private Order order;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "commissionBasis", com.pureeats.domain.enums.CommissionBasis.FULL_ORDER);
        org.mockito.Mockito.lenient().when(orderPricingService.riderCommissionRate(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            com.pureeats.domain.entity.DeliveryGuyDetail d = inv.getArgument(0);
            return d != null && d.getCommissionRate() != null ? d.getCommissionRate() : java.math.BigDecimal.TEN;
        });
        User riderUser = new User();
        riderUser.setId(RIDER);
        riderUser.setDeliveryGuyDetailId(4);
        lenient().when(userRepository.findById(RIDER)).thenReturn(Optional.of(riderUser));
        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        detail.setId(4L);
        lenient().when(deliveryGuyDetailRepository.findById(4L)).thenReturn(Optional.of(detail));

        order = new Order();
        order.setId(77L);
        order.setUserId((int) CUSTOMER);
        order.setRestaurantId(1);
        order.setOrderstatusId(5);
        order.setLocation("{\"latitude\":\"12.9100\",\"longitude\":\"77.6400\"}");
        lenient().when(orderRepository.findById(77L)).thenReturn(Optional.of(order));
        lenient().when(orderService.findOrThrow(77L)).thenReturn(order);
        lenient().when(orderStatusService.codeFor(5)).thenReturn(OrderStatusCode.PICKED_UP);

        Restaurant restaurant = new Restaurant();
        restaurant.setLatitude("12.9300");
        restaurant.setLongitude("77.6200");
        lenient().when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(77);
        accept.setUserId((int) RIDER);
        accept.setIsComplete(false);
        lenient().when(acceptDeliveryRepository.findByUserIdAndIsCompleteFalse((int) RIDER)).thenReturn(List.of(accept));
        lenient().when(acceptDeliveryRepository.findByOrderId(77)).thenReturn(Optional.of(accept));
    }

    private GpsTable gps(String lat, String lng, LocalDateTime at) {
        GpsTable g = new GpsTable();
        g.setOrderId(77);
        g.setDeliveryLat(lat);
        g.setDeliveryLong(lng);
        g.setCreatedAt(at);
        g.setUpdatedAt(at);
        return g;
    }

    @Test
    void riderPing_whileOutOnAnOrder_appendsABreadcrumbToThatOrder() {
        when(gpsTableRepository.findFirstByOrderIdOrderByUpdatedAtDesc(77)).thenReturn(Optional.of(gps("12.9200", "77.6300", LocalDateTime.now().minusSeconds(20))));

        service.updateMyLocation(RIDER, "12.9180", "77.6320");

        ArgumentCaptor<GpsTable> saved = ArgumentCaptor.forClass(GpsTable.class);
        verify(gpsTableRepository).save(saved.capture());
        assertEquals(77, saved.getValue().getOrderId());
        assertEquals("12.9180", saved.getValue().getDeliveryLat());
        assertNotNull(saved.getValue().getCreatedAt());
    }

    @Test
    void riderPing_thatBarelyMoved_onlyRefreshesTheLastPoint() {
        GpsTable last = gps("12.92000", "77.63000", LocalDateTime.now().minusSeconds(20));
        when(gpsTableRepository.findFirstByOrderIdOrderByUpdatedAtDesc(77)).thenReturn(Optional.of(last));

        service.updateMyLocation(RIDER, "12.92001", "77.63001");

        verify(gpsTableRepository).save(last);
        assertTrue(last.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void riderPing_forAnOrderNotOnTheRoad_recordsNothing() {
        when(orderStatusService.codeFor(5)).thenReturn(OrderStatusCode.CANCELLED);
        service.updateMyLocation(RIDER, "12.9180", "77.6320");
        verify(gpsTableRepository, never()).save(any());
    }

    @Test
    void tracking_returnsOrderDestination_riderPosition_andPath() {
        when(gpsTableRepository.findByOrderIdOrderByCreatedAtAsc(77)).thenReturn(List.of(
                gps("12.9300", "77.6200", LocalDateTime.now().minusMinutes(5)),
                gps("12.9200", "77.6300", LocalDateTime.now().minusSeconds(15))));

        OrderTrackingResponse r = service.trackingForCustomer(CUSTOMER, 77L);

        assertEquals("PICKED_UP", r.status());
        assertEquals(new BigDecimal("12.9100"), r.destination().lat());
        assertEquals(new BigDecimal("12.9300"), r.restaurant().lat());
        assertEquals(2, r.path().size());
        assertEquals(new BigDecimal("12.9200"), r.rider().lat());
        assertFalse(r.rider().stale());
    }

    @Test
    void tracking_withNoTrailYet_fallsBackToRidersLastKnownPosition() {
        when(gpsTableRepository.findByOrderIdOrderByCreatedAtAsc(77)).thenReturn(List.of());
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(4L).orElseThrow();
        detail.setLastLat(new BigDecimal("12.95"));
        detail.setLastLng(new BigDecimal("77.60"));
        detail.setLastSeenAt(LocalDateTime.now().minusMinutes(10));

        OrderTrackingResponse r = service.trackingForCustomer(CUSTOMER, 77L);

        assertEquals(new BigDecimal("12.95"), r.rider().lat());
        assertTrue(r.rider().stale(), "a 10-minute-old fix is flagged stale");
        assertTrue(r.path().isEmpty());
    }

    @Test
    void tracking_isOwnerOnly() {
        assertThrows(ForbiddenException.class, () -> service.trackingForCustomer(999L, 77L));
    }

    // ---- tip + pickup/drop distance ----

    @Test
    void availableOrders_includeTipAndPickupAndDropDistance() {
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(4L).orElseThrow();
        detail.setCommissionRate(BigDecimal.TEN);
        detail.setLastLat(new BigDecimal("12.9500"));
        detail.setLastLng(new BigDecimal("77.6000"));
        order.setDeliveryType(0);
        order.setTotal(new BigDecimal("400"));
        order.setDeliveryCharge(new BigDecimal("30"));
        order.setDriverTipAmount(new BigDecimal("25"));
        order.setCreatedAt(LocalDateTime.now());
        when(orderStatusService.idFor(any())).thenReturn(2);
        when(orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(order));
        when(acceptDeliveryRepository.findByOrderId(77)).thenReturn(Optional.empty());
        when(orderPricingService.distanceKm(any(Restaurant.class), any(), any())).thenReturn(new BigDecimal("3.2"));
        when(orderPricingService.distanceKm("12.9500", "77.6000", "12.9300", "77.6200")).thenReturn(new BigDecimal("1.4"));

        var available = service.availableOrders(RIDER);

        assertEquals(1, available.size());
        assertEquals(new BigDecimal("25"), available.get(0).tipAmount());
        assertEquals(new BigDecimal("1.4"), available.get(0).pickupDistanceKm());
        assertEquals(new BigDecimal("3.2"), available.get(0).dropDistanceKm());
        assertEquals(new BigDecimal("40.00"), available.get(0).payoutEstimate(), "payout is commission only; the tip is separate");
    }

    @Test
    void availableOrders_withoutRiderPosition_haveNoPickupDistance() {
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(4L).orElseThrow();
        detail.setCommissionRate(BigDecimal.TEN);
        order.setDeliveryType(0);
        order.setTotal(new BigDecimal("400"));
        order.setDeliveryCharge(new BigDecimal("30"));
        order.setCreatedAt(LocalDateTime.now());
        when(orderStatusService.idFor(any())).thenReturn(2);
        when(orderRepository.findByOrderstatusIdInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(order));
        when(acceptDeliveryRepository.findByOrderId(77)).thenReturn(Optional.empty());
        when(orderPricingService.distanceKm(any(Restaurant.class), any(), any())).thenReturn(new BigDecimal("3.2"));

        var available = service.availableOrders(RIDER);

        assertNull(available.get(0).pickupDistanceKm());
        assertEquals(0, available.get(0).tipAmount().signum());
    }

    @Test
    void deliver_creditsTheTipToTheRiderInFull_andCountsItInTheTripEarning() {
        DeliveryGuyDetail detail = deliveryGuyDetailRepository.findById(4L).orElseThrow();
        detail.setCommissionRate(BigDecimal.TEN);
        order.setUniqueOrderId("PE-77");
        order.setDeliveryPin("1234");
        order.setPaymentMode("RAZORPAY");
        order.setTotal(new BigDecimal("400"));
        order.setRestaurantCharge(BigDecimal.ZERO);
        order.setDeliveryCharge(new BigDecimal("30"));
        order.setDriverTipAmount(new BigDecimal("25"));
        when(orderStatusService.idFor(any())).thenReturn(8);

        service.deliver(RIDER, 77L, "1234");

        verify(walletService).credit(eq(RIDER), eq(new BigDecimal("40.00")), contains("Delivery earning"));
        verify(walletService).credit(eq(RIDER), eq(new BigDecimal("25")), contains("Tip for order #PE-77"));
        ArgumentCaptor<TripDetail> trip = ArgumentCaptor.forClass(TripDetail.class);
        verify(tripDetailRepository).save(trip.capture());
        assertEquals(new BigDecimal("65.00"), trip.getValue().getRiderEarning());
        assertTrue(trip.getValue().getMeta().contains("\"tip\":25"));
    }
}
