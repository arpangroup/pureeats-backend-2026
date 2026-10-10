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
import com.pureeats.rating.repository.RatingRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Builds the customer-facing delivery partner profile for one of the caller's own orders. Lives in
 * rating-service because it needs ratings plus user/order data - the only module that depends on
 * all of them.
 */
@Service
@RequiredArgsConstructor
public class DeliveryPartnerProfileService {

    private static final int TOP_COMPLIMENTS = 4;
    private static final int RECENT_REVIEWS = 3;

    private final OrderRepository orderRepository;
    private final AcceptDeliveryRepository acceptDeliveryRepository;
    private final TripDetailRepository tripDetailRepository;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final RatingRepository ratingRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final com.pureeats.order.service.OrderStatusService orderStatusService;

    @Transactional(readOnly = true)
    public DeliveryPartnerProfileResponse forCustomerOrder(Long customerUserId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getUserId().equals(customerUserId.intValue())) {
            throw new ForbiddenException("This order does not belong to you");
        }
        AcceptDelivery assignment = acceptDeliveryRepository.findByOrderId(order.getId().intValue())
                .orElseThrow(() -> new ResourceNotFoundException("No delivery partner has been assigned to this order yet"));
        Long riderUserId = assignment.getUserId().longValue();
        User riderUser = userRepository.findById(riderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found"));
        DeliveryGuyDetail detail = riderUser.getDeliveryGuyDetailId() != null
                ? deliveryGuyDetailRepository.findById(riderUser.getDeliveryGuyDetailId().longValue()).orElse(null)
                : null;

        String photoKey = detail != null && detail.getPhoto() != null && !detail.getPhoto().isBlank() ? detail.getPhoto() : riderUser.getPhoto();
        List<Rating> ratings = detail != null
                ? ratingRepository.findByRateableTypeAndRateableId(RateableType.DRIVER.legacyMorphClass(), detail.getId())
                : List.of();
        List<TripDetail> trips = tripDetailRepository.findByRiderId(riderUserId.intValue());
        // Only DELIVERED orders count as completed trips. An assignment is also closed (isComplete) when its order is
        // cancelled/returned - to free the partner - so counting those used to include cancelled orders.
        int completed = deliveredTrips(riderUserId);
        int forYou = (int) trips.stream().filter(t -> t.getCustomerId() != null && t.getCustomerId().equals(customerUserId.intValue())).count();
        BigDecimal distance = trips.stream().map(TripDetail::getDistanceTravelled).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);

        BigDecimal average = ratings.isEmpty() ? null
                : BigDecimal.valueOf(ratings.stream().mapToInt(r -> r.getRating() != null ? r.getRating() : 0).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP);
        List<DeliveryPartnerProfileResponse.StarCount> breakdown = new ArrayList<>();
        for (int stars = 5; stars >= 1; stars--) {
            int s = stars;
            breakdown.add(new DeliveryPartnerProfileResponse.StarCount(s,
                    (int) ratings.stream().filter(r -> r.getRating() != null && r.getRating() == s).count()));
        }

        boolean verified = (detail == null || !Boolean.FALSE.equals(detail.getIsActive())) && User.STATUS_ACTIVE.equals(riderUser.getIsActive());

        return new DeliveryPartnerProfileResponse(
                detail != null ? detail.getId() : riderUserId,
                detail != null && detail.getName() != null && !detail.getName().isBlank() ? detail.getName() : riderUser.getName(),
                mediaUrlResolver.resolve(photoKey),
                detail != null ? detail.getVehicleNumber() : null,
                riderUser.getPhone(),
                verified,
                average,
                ratings.size(),
                breakdown,
                completed,
                forYou,
                distance,
                detail != null && detail.getCreatedAt() != null ? detail.getCreatedAt() : riderUser.getCreatedAt(),
                topCompliments(ratings),
                recentReviews(ratings));
    }

    /** Stored tags are "a,b,c"; tolerate legacy "[a, b]" / quoted forms too. */
    static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.replace("[", "").replace("]", "").replace("\"", "").split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    private static List<DeliveryPartnerProfileResponse.Compliment> topCompliments(List<Rating> ratings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Rating r : ratings) {
            for (String tag : parseTags(r.getTags())) counts.merge(tag, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_COMPLIMENTS)
                .map(e -> new DeliveryPartnerProfileResponse.Compliment(e.getKey(), e.getValue()))
                .toList();
    }

    private List<DeliveryPartnerProfileResponse.Review> recentReviews(List<Rating> ratings) {
        return ratings.stream()
                .filter(r -> r.getComment() != null && !r.getComment().isBlank())
                .sorted(Comparator.comparing(Rating::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_REVIEWS)
                .map(r -> new DeliveryPartnerProfileResponse.Review(r.getRating() != null ? r.getRating() : 0, r.getComment().trim(),
                        reviewerName(r), r.getCreatedAt()))
                .toList();
    }

    private String reviewerName(Rating r) {
        String full = r.getUserId() != null ? userRepository.findById(r.getUserId()).map(User::getName).orElse(null) : r.getName();
        if (full == null || full.isBlank()) return "A customer";
        String[] parts = full.trim().split("\\s+");
        return parts.length > 1 ? parts[0] + " " + parts[parts.length - 1].charAt(0) + "." : parts[0];
    }

    /** Distinct orders this partner was assigned that ended DELIVERED (covers trips recorded before trip_details). */
    private int deliveredTrips(Long riderUserId) {
        java.util.List<Long> orderIds = acceptDeliveryRepository.findByUserIdOrderByIdDesc(riderUserId.intValue()).stream()
                .map(a -> a.getOrderId()).filter(java.util.Objects::nonNull).map(Integer::longValue).distinct().toList();
        if (orderIds.isEmpty()) return 0;
        Integer delivered = orderStatusService.idFor(com.pureeats.domain.enums.OrderStatusCode.DELIVERED);
        return (int) orderRepository.findAllById(orderIds).stream().filter(o -> java.util.Objects.equals(o.getOrderstatusId(), delivered)).count();
    }
}
