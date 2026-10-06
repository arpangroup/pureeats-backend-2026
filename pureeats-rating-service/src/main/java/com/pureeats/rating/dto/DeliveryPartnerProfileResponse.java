package com.pureeats.rating.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What a customer sees about the delivery partner on their order (tracking page bottom sheet).
 * Deliberately customer-safe: no age, gender, commission, earnings or location history - only
 * identity, reputation and track record.
 */
public record DeliveryPartnerProfileResponse(
        Long id,
        String name,
        String photo,
        String vehicleNumber,
        String phone,
        boolean verified,
        /** Average of customer ratings, 1 decimal; null until the partner has been rated. */
        BigDecimal rating,
        int ratingCount,
        /** Count per star, 5 down to 1. */
        List<StarCount> ratingBreakdown,
        int completedTrips,
        /** Deliveries this partner has completed for THIS customer before. */
        int deliveriesForYou,
        BigDecimal totalDistanceKm,
        LocalDateTime memberSince,
        List<Compliment> topCompliments,
        List<Review> recentReviews
) {
    public record StarCount(int stars, int count) {
    }

    public record Compliment(String label, int count) {
    }

    /** {@code reviewerName} is first name + last initial only. */
    public record Review(int rating, String comment, String reviewerName, LocalDateTime createdAt) {
    }
}
