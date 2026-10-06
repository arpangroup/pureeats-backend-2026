package com.pureeats.rating.service;

import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.*;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.order.repository.AcceptDeliveryRepository;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.repository.TripDetailRepository;
import com.pureeats.rating.dto.DeliveryPartnerProfileResponse;
import com.pureeats.rating.dto.RateableType;
import com.pureeats.rating.dto.SubmitRatingRequest;
import com.pureeats.rating.repository.RatingRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryPartnerProfileServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AcceptDeliveryRepository acceptDeliveryRepository;
    @Mock private TripDetailRepository tripDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private MediaUrlResolver mediaUrlResolver;
    @InjectMocks private DeliveryPartnerProfileService service;

    @BeforeEach
    void setUp() {
        Order order = new Order();
        order.setId(5L);
        order.setUserId(30);
        lenient().when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        AcceptDelivery accept = new AcceptDelivery();
        accept.setOrderId(5);
        accept.setUserId(9);
        lenient().when(acceptDeliveryRepository.findByOrderId(5)).thenReturn(Optional.of(accept));

        User rider = new User();
        rider.setId(9L);
        rider.setName("Ravi Kumar");
        rider.setPhone("9000000000");
        rider.setIsActive(User.STATUS_ACTIVE);
        rider.setDeliveryGuyDetailId(4);
        lenient().when(userRepository.findById(9L)).thenReturn(Optional.of(rider));
        User reviewer = new User();
        reviewer.setId(31L);
        reviewer.setName("Asha Menon");
        lenient().when(userRepository.findById(31L)).thenReturn(Optional.of(reviewer));

        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        detail.setId(4L);
        detail.setName("Ravi Kumar");
        detail.setVehicleNumber("KA01AB1234");
        detail.setIsActive(true);
        detail.setCreatedAt(LocalDateTime.of(2025, 3, 1, 10, 0));
        lenient().when(deliveryGuyDetailRepository.findById(4L)).thenReturn(Optional.of(detail));
        lenient().when(mediaUrlResolver.resolve(any())).thenReturn("https://cdn/photo.jpg");
    }

    private static TripDetail trip(int customerId, String km) {
        TripDetail t = new TripDetail();
        t.setCustomerId(customerId);
        t.setDistanceTravelled(new BigDecimal(km));
        return t;
    }

    private static Rating rating(int stars, String tags, String comment, long userId, int daysAgo) {
        Rating r = new Rating();
        r.setRating(stars);
        r.setTags(tags);
        r.setComment(comment);
        r.setUserId(userId);
        r.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return r;
    }

    @Test
    void profile_aggregatesTripsRatingsComplimentsAndReviews() {
        when(tripDetailRepository.findByRiderId(9)).thenReturn(List.of(trip(30, "2.5"), trip(30, "1.5"), trip(44, "3.0")));
        when(ratingRepository.findByRateableTypeAndRateableId(RateableType.DRIVER.legacyMorphClass(), 4L)).thenReturn(List.of(
                rating(5, "Polite,On time", "Super quick!", 31, 1),
                rating(4, "[\"On time\"]", null, 31, 2),
                rating(5, "On time", "Very careful with the food", 31, 3)));

        DeliveryPartnerProfileResponse p = service.forCustomerOrder(30L, 5L);

        assertEquals("Ravi Kumar", p.name());
        assertEquals(3, p.completedTrips());
        assertEquals(2, p.deliveriesForYou());
        assertEquals(new BigDecimal("7.0"), p.totalDistanceKm());
        assertEquals(new BigDecimal("4.7"), p.rating());
        assertEquals(3, p.ratingCount());
        assertEquals(2, p.ratingBreakdown().get(0).count(), "two 5-star ratings");
        assertEquals("On time", p.topCompliments().get(0).label());
        assertEquals(3, p.topCompliments().get(0).count());
        assertEquals(2, p.recentReviews().size(), "only ratings with a comment are reviews");
        assertEquals("Asha M.", p.recentReviews().get(0).reviewerName());
        assertEquals("Super quick!", p.recentReviews().get(0).comment());
        assertTrue(p.verified());
    }

    @Test
    void profile_withNoRatingsYet_hasNullAverage() {
        when(tripDetailRepository.findByRiderId(9)).thenReturn(List.of());
        when(ratingRepository.findByRateableTypeAndRateableId(any(), any())).thenReturn(List.of());
        DeliveryPartnerProfileResponse p = service.forCustomerOrder(30L, 5L);
        assertNull(p.rating());
        assertEquals(0, p.ratingCount());
    }

    @Test
    void profile_isOwnerOnly() {
        assertThrows(ForbiddenException.class, () -> service.forCustomerOrder(99L, 5L));
    }

    @Test
    void profile_beforeAssignment_isNotFound() {
        when(acceptDeliveryRepository.findByOrderId(5)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.forCustomerOrder(30L, 5L));
    }

    @Test
    void ratingTags_acceptArrayOrCommaString() {
        assertEquals("Polite,On time", new SubmitRatingRequest(1L, RateableType.DRIVER, 4L, 5, null, List.of("Polite", " On time ")).tagsAsString());
        assertEquals("Polite,On time", new SubmitRatingRequest(1L, RateableType.DRIVER, 4L, 5, null, "Polite, On time").tagsAsString());
        assertNull(new SubmitRatingRequest(1L, RateableType.DRIVER, 4L, 5, null, List.of()).tagsAsString());
    }
}
