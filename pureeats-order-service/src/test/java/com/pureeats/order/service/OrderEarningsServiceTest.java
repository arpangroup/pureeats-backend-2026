package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.dto.OrderEarningsSplitResponse;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.TripDetailRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEarningsServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderPricingService orderPricingService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AcceptDeliveryRepository acceptDeliveryRepository;
    @Mock private TripDetailRepository tripDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    @Mock private OrderStatusService orderStatusService;
    @InjectMocks private OrderEarningsService service;

    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "commissionBasis", CommissionBasis.FULL_ORDER);
        // items 500, coupon -50, tax 22.50, packaging 20, delivery 30, platform fee 7, tip 20 -> paid 549.50
        order = new Order();
        order.setId(1L);
        order.setRestaurantId(3);
        order.setOrderstatusId(5);
        order.setTotal(new BigDecimal("500"));
        order.setDiscountAmount(new BigDecimal("50"));
        order.setTax(new BigDecimal("22.50"));
        order.setRestaurantCharge(new BigDecimal("20"));
        order.setDeliveryCharge(new BigDecimal("30"));
        order.setPlatformFee(new BigDecimal("7"));
        order.setDriverTipAmount(new BigDecimal("20"));
        order.setPayable(new BigDecimal("549.50"));
        when(orderService.findOrThrow(1L)).thenReturn(order);
        when(orderService.breakdownOf(order)).thenReturn(null);
        when(restaurantRepository.findById(3L)).thenReturn(Optional.of(new Restaurant()));
        when(orderPricingService.commissionPercentage(any())).thenReturn(new BigDecimal("15"));
        when(orderPricingService.commission(new BigDecimal("500"), new BigDecimal("15"))).thenReturn(new BigDecimal("75.00"));
        lenient().when(orderService.restaurantPayoutFor(order)).thenReturn(new BigDecimal("445.00"));
        lenient().when(orderStatusService.codeFor(5)).thenReturn(OrderStatusCode.PICKED_UP);
        when(tripDetailRepository.findByOrderId(1)).thenReturn(Optional.empty());
        lenient().when(orderPricingService.riderCommissionRate(any())).thenAnswer(inv -> {
            DeliveryGuyDetail d = inv.getArgument(0);
            return d != null && d.getCommissionRate() != null ? d.getCommissionRate() : BigDecimal.TEN;
        });
    }

    @Test
    void sharesReconcileToWhatTheCustomerPaid() {
        AcceptDelivery accept = new AcceptDelivery();
        accept.setUserId(9);
        when(acceptDeliveryRepository.findByOrderId(1)).thenReturn(Optional.of(accept));
        User rider = new User();
        rider.setId(9L);
        rider.setDeliveryGuyDetailId(4);
        when(userRepository.findById(9L)).thenReturn(Optional.of(rider));
        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        detail.setCommissionRate(BigDecimal.TEN);
        detail.setName("Ravi");
        when(deliveryGuyDetailRepository.findById(4L)).thenReturn(Optional.of(detail));

        OrderEarningsSplitResponse s = service.split(1L);

        assertEquals(new BigDecimal("445.00"), s.restaurant().amount());
        assertEquals(new BigDecimal("50.00"), s.rider().commissionAmount(), "10% of the 500 item total");
        assertEquals(new BigDecimal("70.00"), s.rider().amount(), "commission + 20 tip");
        // 75 commission + 7 fee + 30 delivery - 50 rider commission - 50 coupon = 12
        assertEquals(new BigDecimal("12.00"), s.platform().amount());
        BigDecimal sum = s.restaurant().amount().add(s.rider().amount()).add(s.platform().amount()).add(s.taxCollected());
        assertEquals(0, sum.compareTo(s.customerPaid()), "restaurant + rider + platform + tax = customer paid");
        assertFalse(s.restaurant().finalized());
    }

    @Test
    void withoutARider_theDeliveryChargeStaysWithThePlatform() {
        when(acceptDeliveryRepository.findByOrderId(1)).thenReturn(Optional.empty());
        order.setDriverTipAmount(BigDecimal.ZERO);
        order.setPayable(new BigDecimal("529.50"));

        OrderEarningsSplitResponse s = service.split(1L);

        assertFalse(s.rider().assigned());
        assertEquals(new BigDecimal("62.00"), s.platform().amount(), "75 + 7 + 30 - 0 - 50");
        BigDecimal sum = s.restaurant().amount().add(s.rider().amount()).add(s.platform().amount()).add(s.taxCollected());
        assertEquals(0, sum.compareTo(s.customerPaid()));
    }

    @Test
    void deliveredUnderTheEarlierRule_showsWhatWasRecorded_andStillReconciles() {
        // Recorded at delivery under the old rule: restaurant = items - packaging = 480; rider 70.
        TripDetail trip = new TripDetail();
        trip.setRestaurantEarning(new BigDecimal("480"));
        trip.setRiderEarning(new BigDecimal("70"));
        when(tripDetailRepository.findByOrderId(1)).thenReturn(Optional.of(trip));
        when(orderPricingService.restaurantPayout(new BigDecimal("500"), new BigDecimal("75.00"), new BigDecimal("20"))).thenReturn(new BigDecimal("445.00"));
        lenient().when(orderStatusService.codeFor(5)).thenReturn(OrderStatusCode.DELIVERED);
        AcceptDelivery accept = new AcceptDelivery();
        accept.setUserId(9);
        when(acceptDeliveryRepository.findByOrderId(1)).thenReturn(Optional.of(accept));
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        OrderEarningsSplitResponse s = service.split(1L);

        assertEquals(new BigDecimal("480.00"), s.restaurant().amount(), "the recorded amount, not today's formula (445)");
        assertTrue(s.restaurant().recordedUnderEarlierRule());
        assertTrue(s.restaurant().finalized());
        assertEquals(new BigDecimal("70.00"), s.rider().amount());
        BigDecimal sum = s.restaurant().amount().add(s.rider().amount()).add(s.platform().amount()).add(s.taxCollected());
        assertEquals(0, sum.compareTo(s.customerPaid()), "still reconciles to what the customer paid");
    }
}
