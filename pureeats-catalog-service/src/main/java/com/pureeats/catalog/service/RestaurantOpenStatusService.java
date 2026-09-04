package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.DayScheduleDto;
import com.pureeats.catalog.dto.RestaurantOpenStatus;
import com.pureeats.catalog.dto.TimeSlotDto;
import com.pureeats.domain.entity.Restaurant;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one place "is this restaurant open right now" gets decided from a real per-day, multi-slot
 * {@code weeklySchedule} - both {@code RestaurantService} (customer-facing API responses) and
 * order-service's {@code RestaurantAvailabilityRule} (the checkout gate) call this instead of each
 * independently re-deriving an answer from the legacy single {@code openingTime}/{@code closingTime}
 * pair, which only ever reflected whichever day happened to come first in the week - see
 * {@code RESTAURANT_DOMAIN_ARCHITECTURE.md §5} for how that gap was found.
 * <p>
 * A restaurant that never configured a weekly schedule (an older record, or one an owner hasn't
 * touched the "Operating hours" section on yet) falls back to the legacy single-window check
 * unchanged - this is purely additive, not a breaking change for anything that predates it.
 */
@Component
public class RestaurantOpenStatusService {

    /** How close to a slot's close time counts as "closing soon". */
    private static final long CLOSING_SOON_MINUTES = 30;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public RestaurantOpenStatus compute(Restaurant restaurant, List<DayScheduleDto> weeklySchedule, LocalDateTime now) {
        if (!Boolean.TRUE.equals(restaurant.getIsActive()) || !Boolean.TRUE.equals(restaurant.getIsAccepted())) {
            return closed(null, null);
        }
        if (weeklySchedule == null || weeklySchedule.isEmpty()) {
            return legacyFallback(restaurant, now);
        }

        Map<String, DayScheduleDto> byDay = weeklySchedule.stream()
                .collect(Collectors.toMap(d -> d.day().toLowerCase(Locale.ROOT), d -> d, (a, b) -> a));

        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();

        DayScheduleDto todaySchedule = byDay.get(dayKey(today.getDayOfWeek()));
        if (todaySchedule != null && todaySchedule.isOpen() && todaySchedule.slots() != null) {
            List<TimeSlotDto> slots = sortedSlots(todaySchedule);

            // Currently inside one of today's slots?
            for (TimeSlotDto slot : slots) {
                LocalTime open = LocalTime.parse(slot.open(), TIME_FORMAT);
                LocalTime close = LocalTime.parse(slot.close(), TIME_FORMAT);
                if (!nowTime.isBefore(open) && nowTime.isBefore(close)) {
                    boolean closingSoon = Duration.between(nowTime, close).toMinutes() <= CLOSING_SOON_MINUTES;
                    return new RestaurantOpenStatus(true, closingSoon, slot.close(), null, null);
                }
            }
            // Not open yet, but a later slot starts today (e.g. it's 3pm, dinner opens at 6pm)?
            for (TimeSlotDto slot : slots) {
                LocalTime open = LocalTime.parse(slot.open(), TIME_FORMAT);
                if (nowTime.isBefore(open)) {
                    return closed(slot.open(), "today");
                }
            }
        }

        // Past today's hours (or today's closed) - scan forward for the next open day.
        for (int offset = 1; offset <= 7; offset++) {
            LocalDate candidate = today.plusDays(offset);
            DayScheduleDto schedule = byDay.get(dayKey(candidate.getDayOfWeek()));
            if (schedule != null && schedule.isOpen() && schedule.slots() != null && !schedule.slots().isEmpty()) {
                TimeSlotDto first = sortedSlots(schedule).get(0);
                String label = offset == 1 ? "tomorrow" : dayKey(candidate.getDayOfWeek());
                return closed(first.open(), label);
            }
        }

        // No open day anywhere in the week.
        return closed(null, null);
    }

    private List<TimeSlotDto> sortedSlots(DayScheduleDto day) {
        return day.slots().stream()
                .sorted(Comparator.comparing(s -> LocalTime.parse(s.open(), TIME_FORMAT)))
                .toList();
    }

    private String dayKey(DayOfWeek dayOfWeek) {
        return dayOfWeek.toString().toLowerCase(Locale.ROOT);
    }

    private RestaurantOpenStatus closed(String nextOpensAt, String nextOpensLabel) {
        return new RestaurantOpenStatus(false, false, null, nextOpensAt, nextOpensLabel);
    }

    /** Mirrors RestaurantAvailabilityRule's original single-window check exactly, for restaurants with no weeklySchedule yet. */
    private RestaurantOpenStatus legacyFallback(Restaurant restaurant, LocalDateTime now) {
        LocalTime opening = restaurant.getOpeningTime();
        LocalTime closing = restaurant.getClosingTime();
        if (opening == null || closing == null) {
            return new RestaurantOpenStatus(true, false, null, null, null);
        }
        LocalTime nowTime = now.toLocalTime();
        boolean open = opening.isBefore(closing)
                ? !nowTime.isBefore(opening) && !nowTime.isAfter(closing)
                : !nowTime.isBefore(opening) || !nowTime.isAfter(closing);
        if (!open) {
            return closed(opening.format(TIME_FORMAT), null);
        }
        boolean closingSoon = !opening.isBefore(closing) // overnight window - "soon" is ambiguous across midnight, skip it
                ? false
                : Duration.between(nowTime, closing).toMinutes() <= CLOSING_SOON_MINUTES;
        return new RestaurantOpenStatus(true, closingSoon, closing.format(TIME_FORMAT), null, null);
    }
}
