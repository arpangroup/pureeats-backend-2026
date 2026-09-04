package com.pureeats.catalog.dto;

import java.util.List;

/**
 * One day's operating hours - {@code day} is a lowercase weekday name ("monday".."sunday").
 * A closed day still round-trips with {@code isOpen: false} and an empty/irrelevant slot list.
 */
public record DayScheduleDto(String day, boolean isOpen, List<TimeSlotDto> slots) {
}
