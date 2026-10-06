package com.pureeats.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Earnings analytics for one period: DAY / WEEK / MONTH (today / this week / this month) or CUSTOM
 * (any from..to range). Totals for the period, the same totals for the equally long period right
 * before it (for the "vs last week" comparison), a time series for the bar chart, and breakdowns
 * (busiest hours, weekday pattern, payment split, top restaurants).
 */
public record RiderEarningsAnalyticsResponse(
        String period,
        LocalDate currentFrom,
        LocalDate currentTo,
        Totals current,
        LocalDate previousFrom,
        LocalDate previousTo,
        Totals previous,
        /** Percent change of current vs previous earnings; null when the previous period earned nothing. */
        BigDecimal changePercent,
        /** DAY, WEEK or MONTH - the size of each entry in {@code buckets}. */
        String bucketSize,
        /** DAY/WEEK/MONTH: the recent trend (last 7 days / 8 weeks / 6 months, oldest first). CUSTOM: the chosen range itself. */
        List<Bucket> buckets,
        /** The best entry in {@code buckets} by earnings, or null when nothing was earned. */
        Bucket bestBucket,
        /** 24 entries (hour 0-23) for the selected period - when deliveries happen. */
        List<HourBucket> byHour,
        /** 7 entries Mon..Sun for the selected period. */
        List<Bucket> byWeekday,
        PaymentSplit paymentSplit,
        List<RestaurantShare> topRestaurants
) {
    public record Totals(BigDecimal earnings, int trips, BigDecimal averagePerTrip, BigDecimal distanceKm,
                         BigDecimal averageDistanceKm, BigDecimal codCollected, int activeDays) {
    }

    public record Bucket(String label, LocalDate from, LocalDate to, BigDecimal earnings, int trips) {
    }

    public record HourBucket(int hour, BigDecimal earnings, int trips) {
    }

    public record PaymentSplit(int codTrips, BigDecimal codCollected, int onlineTrips) {
    }

    public record RestaurantShare(Long restaurantId, String restaurantName, int trips, BigDecimal earnings) {
    }
}
