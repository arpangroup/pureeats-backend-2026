package com.pureeats.catalog.dto;

/**
 * The real-time open/closed answer, computed from a restaurant's actual per-day, multi-slot
 * {@code weeklySchedule} (today's weekday, checked against today's slots) rather than the legacy
 * single {@code openingTime}/{@code closingTime} pair, which only ever reflected whichever day
 * happened to come first in the week - see {@code RestaurantOpenStatusService}.
 *
 * @param isOpenNow      true if the current time falls inside one of today's slots (and the restaurant is active/accepted)
 * @param isClosingSoon  true only when {@code isOpenNow} and the current slot's close time is within the "closing soon" window
 * @param closesAt       "HH:mm", set only when {@code isOpenNow} - the current slot's close time
 * @param nextOpensAt    "HH:mm", set only when {@code !isOpenNow} and a future opening exists this week
 * @param nextOpensLabel "today" | "tomorrow" | a lowercase weekday name, set only alongside {@code nextOpensAt}
 */
public record RestaurantOpenStatus(
        boolean isOpenNow,
        boolean isClosingSoon,
        String closesAt,
        String nextOpensAt,
        String nextOpensLabel
) {
}
