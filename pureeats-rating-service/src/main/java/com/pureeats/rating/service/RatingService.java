package com.pureeats.rating.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.entity.Order;
import com.pureeats.domain.entity.Rating;
import com.pureeats.domain.enums.OrderStatusCode;
import com.pureeats.order.repository.OrderRepository;
import com.pureeats.order.service.OrderStatusService;
import com.pureeats.rating.dto.*;
import com.pureeats.rating.repository.RatingRepository;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;

    @Transactional(readOnly = true)
    public List<RatableOrderResponse> ratableOrders(Long userId) {
        Integer deliveredStatusId = orderStatusService.idFor(OrderStatusCode.DELIVERED);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId.intValue()).stream()
                .filter(o -> o.getOrderstatusId().equals(deliveredStatusId))
                .filter(o -> ratingRepository.findByOrderId(o.getId().intValue()).isEmpty())
                .map(o -> new RatableOrderResponse(o.getId(), o.getUniqueOrderId(), o.getRestaurantId().longValue()))
                .toList();
    }

    @Transactional
    public RatingResponse submit(Long userId, SubmitRatingRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new BadRequestException("Order not found: " + request.orderId()));
        if (!order.getUserId().equals(userId.intValue())) {
            throw new ForbiddenException("This order does not belong to you");
        }
        if (orderStatusService.codeFor(order.getOrderstatusId()) != OrderStatusCode.DELIVERED) {
            throw new BadRequestException("You can only rate delivered orders");
        }

        String morphType = request.rateableType().legacyMorphClass();
        boolean alreadyRated = ratingRepository.findByOrderId(order.getId().intValue()).stream()
                .anyMatch(r -> r.getRateableType().equals(morphType));
        if (alreadyRated) {
            throw new BadRequestException("You have already rated this " + request.rateableType().name().toLowerCase());
        }

        Rating rating = new Rating();
        rating.setOrderId(order.getId().intValue());
        rating.setUserId(userId);
        rating.setRateableType(morphType);
        rating.setRateableId(request.rateableId());
        rating.setRating(request.rating());
        rating.setComment(request.comment());
        rating.setTags(request.tags());
        rating.setCreatedAt(LocalDateTime.now());
        rating.setUpdatedAt(LocalDateTime.now());
        rating = ratingRepository.save(rating);

        if (request.rateableType() == RateableType.DRIVER) {
            recalculateDriverRating(request.rateableId());
        }

        return toResponse(rating);
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> restaurantRatings(Long restaurantId) {
        return ratingRepository.findByRateableTypeAndRateableId(RateableType.RESTAURANT.legacyMorphClass(), restaurantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AverageRatingResponse restaurantAverage(Long restaurantId) {
        return average(RateableType.RESTAURANT, restaurantId);
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> driverRatings(Long deliveryGuyDetailId) {
        return ratingRepository.findByRateableTypeAndRateableId(RateableType.DRIVER.legacyMorphClass(), deliveryGuyDetailId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AverageRatingResponse driverAverage(Long deliveryGuyDetailId) {
        return average(RateableType.DRIVER, deliveryGuyDetailId);
    }

    private AverageRatingResponse average(RateableType type, Long rateableId) {
        List<Rating> ratings = ratingRepository.findByRateableTypeAndRateableId(type.legacyMorphClass(), rateableId);
        double avg = ratings.stream().mapToInt(Rating::getRating).average().orElse(0);
        return new AverageRatingResponse(avg, ratings.size());
    }

    private void recalculateDriverRating(Long deliveryGuyDetailId) {
        AverageRatingResponse avg = average(RateableType.DRIVER, deliveryGuyDetailId);
        deliveryGuyDetailRepository.findById(deliveryGuyDetailId).ifPresent(detail -> {
            detail.setRating(BigDecimal.valueOf(avg.average()).setScale(2, RoundingMode.HALF_UP));
            deliveryGuyDetailRepository.save(detail);
        });
    }

    private RatingResponse toResponse(Rating r) {
        RateableType type = r.getRateableType().equals(RateableType.RESTAURANT.legacyMorphClass())
                ? RateableType.RESTAURANT : RateableType.DRIVER;
        return new RatingResponse(r.getId(), r.getOrderId().longValue(), type, r.getRateableId(), r.getRating(),
                r.getComment(), r.getTags(), r.getName(), r.getCreatedAt());
    }
}
