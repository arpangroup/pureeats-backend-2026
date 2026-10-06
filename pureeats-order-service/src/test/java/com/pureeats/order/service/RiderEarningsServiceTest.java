package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.DeliveryCollection;
import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.RiderSettlement;
import com.pureeats.domain.entity.TripDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.order.dto.RiderEarningsAnalyticsResponse;
import com.pureeats.order.dto.RiderEarningsSummaryResponse;
import com.pureeats.order.dto.RiderSettlementResponse;
import com.pureeats.order.dto.SettleRiderRequest;
import com.pureeats.order.repository.*;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiderEarningsServiceTest {

    private static final long RIDER = 42L;

    @Mock private TripDetailRepository tripDetailRepository;
    @Mock private RiderSettlementRepository riderSettlementRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private UserRepository userRepository;
    @Mock private DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    @Mock private DeliveryCollectionRepository deliveryCollectionRepository;
    @Mock private DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    @Mock private WalletService walletService;

    private RiderEarningsService service;
    private final List<TripDetail> trips = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new RiderEarningsService(tripDetailRepository, riderSettlementRepository, orderRepository, restaurantRepository,
                userRepository, deliveryGuyDetailRepository, deliveryCollectionRepository, deliveryCollectionLogRepository, walletService);
        ReflectionTestUtils.setField(service, "commissionBasis", CommissionBasis.FULL_ORDER);
        lenient().when(tripDetailRepository.findByRiderId((int) RIDER)).thenReturn(trips);
        lenient().when(riderSettlementRepository.findByRiderUserIdOrderByCreatedAtDesc(RIDER)).thenReturn(List.of());
        User user = new User();
        user.setId(RIDER);
        user.setDeliveryGuyDetailId(7);
        lenient().when(userRepository.findById(RIDER)).thenReturn(Optional.of(user));
        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        detail.setId(7L);
        detail.setCommissionRate(BigDecimal.TEN);
        lenient().when(deliveryGuyDetailRepository.findById(7L)).thenReturn(Optional.of(detail));
    }

    private TripDetail trip(String earning, String cod, LocalDateTime at, boolean settled) {
        TripDetail t = new TripDetail();
        t.setId((long) trips.size() + 1);
        t.setOrderId(trips.size() + 100);
        t.setRestaurantId(1);
        t.setRiderId((int) RIDER);
        t.setRiderEarning(new BigDecimal(earning));
        t.setCashCollectedFromCustomer(new BigDecimal(cod));
        t.setDistanceTravelled(BigDecimal.valueOf(2));
        t.setIsSettlementDone(settled ? 1 : 0);
        t.setCreatedAt(at);
        trips.add(t);
        return t;
    }

    @Test
    void summary_netsPendingEarningsAgainstCashInHand_andIgnoresSettledTrips() {
        trip("40", "0", LocalDateTime.now(), false);
        trip("30", "250", LocalDateTime.now(), false);
        trip("99", "500", LocalDateTime.now().minusDays(3), true);

        RiderEarningsSummaryResponse s = service.summary(RIDER);

        assertEquals(new BigDecimal("169.00"), s.lifetimeEarnings());
        assertEquals(new BigDecimal("70.00"), s.pendingEarnings());
        assertEquals(new BigDecimal("250.00"), s.cashInHand());
        assertEquals(new BigDecimal("-180.00"), s.netPending());
        assertEquals(RiderSettlement.DIRECTION_COLLECTED_FROM_RIDER, s.netDirection());
        assertEquals(2, s.unsettledTrips());
    }

    @Test
    void settle_marksPendingTripsSettled_debitsWallet_andClearsCod() {
        TripDetail a = trip("40", "0", LocalDateTime.now(), false);
        TripDetail b = trip("30", "100", LocalDateTime.now(), false);
        TripDetail old = trip("99", "0", LocalDateTime.now().minusDays(3), true);
        when(riderSettlementRepository.save(any(RiderSettlement.class))).thenAnswer(inv -> {
            RiderSettlement s = inv.getArgument(0);
            s.setId(5L);
            return s;
        });
        DeliveryCollection collection = new DeliveryCollection();
        collection.setId(3L);
        collection.setAmount(new BigDecimal("100"));
        when(deliveryCollectionRepository.findByUserId((int) RIDER)).thenReturn(Optional.of(collection));

        RiderSettlementResponse result = service.settle(1L, RIDER, new SettleRiderRequest("UPI", "UTR123", null));

        assertEquals(new BigDecimal("70.00"), result.earningsAmount());
        assertEquals(new BigDecimal("100.00"), result.codAmount());
        assertEquals(new BigDecimal("-30.00"), result.netAmount());
        assertEquals(2, result.tripCount());
        assertEquals(5L, a.getSettlementId());
        assertEquals(5L, b.getSettlementId());
        assertNull(old.getSettlementId(), "an already-settled trip is not re-settled");
        verify(walletService).debit(eq(RIDER), eq(new BigDecimal("70.00")), any());
        assertEquals(0, collection.getAmount().signum());
    }

    @Test
    void settle_withNothingPending_isRejected() {
        trip("99", "0", LocalDateTime.now(), true);
        assertThrows(BadRequestException.class, () -> service.settle(1L, RIDER, null));
        verify(walletService, never()).debit(anyLong(), any(), any());
    }

    @Test
    void analytics_custom_comparesWithEquallyLongPreviousRange_andBucketsDaily() {
        LocalDate from = LocalDate.of(2026, 9, 10);
        trip("50", "0", from.atTime(13, 0), false);
        trip("25", "200", from.plusDays(2).atTime(20, 30), false);
        trip("20", "0", from.minusDays(1).atTime(12, 0), false); // previous range

        RiderEarningsAnalyticsResponse r = service.analytics(RIDER, "CUSTOM", from, from.plusDays(4));

        assertEquals("DAY", r.bucketSize());
        assertEquals(5, r.buckets().size());
        assertEquals(new BigDecimal("75.00"), r.current().earnings());
        assertEquals(2, r.current().trips());
        assertEquals(2, r.current().activeDays());
        assertEquals(from.minusDays(5), r.previousFrom());
        assertEquals(new BigDecimal("20.00"), r.previous().earnings());
        assertEquals(new BigDecimal("275.0"), r.changePercent());
        assertEquals(new BigDecimal("50.00"), r.bestBucket().earnings());
        assertEquals(1, r.byHour().get(13).trips());
        assertEquals(1, r.paymentSplit().codTrips());
        assertEquals(1, r.paymentSplit().onlineTrips());
    }

    @Test
    void analytics_customRange_longerThanFourMonths_bucketsMonthly() {
        RiderEarningsAnalyticsResponse r = service.analytics(RIDER, "CUSTOM", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 10));
        assertEquals("MONTH", r.bucketSize());
        assertEquals(6, r.buckets().size());
        assertEquals(LocalDate.of(2026, 1, 15), r.buckets().get(0).from(), "first bucket is clipped to the range start");
    }

    @Test
    void analytics_customRange_toBeforeFrom_isRejected() {
        assertThrows(BadRequestException.class, () -> service.analytics(RIDER, "CUSTOM", LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1)));
    }

    @Test
    void analytics_week_hasEightWeeklyBuckets() {
        RiderEarningsAnalyticsResponse r = service.analytics(RIDER, "WEEK", null, null);
        assertEquals(8, r.buckets().size());
        assertEquals(java.time.DayOfWeek.MONDAY, r.currentFrom().getDayOfWeek());
    }
}
