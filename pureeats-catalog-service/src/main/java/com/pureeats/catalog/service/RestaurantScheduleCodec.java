package com.pureeats.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.dto.DayScheduleDto;
import com.pureeats.catalog.dto.TimeSlotDto;
import com.pureeats.domain.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * (De)serializes a restaurant's weekly operating hours to/from {@code Restaurant.scheduleData} -
 * a JSON blob column that predates this feature and was otherwise unused. Kept as a plain JSON
 * blob rather than a normalized table since a week's worth of slots is always read/written as one
 * unit (the admin form edits/saves the whole week at once, never a single day or slot in isolation).
 */
@Component
@RequiredArgsConstructor
public class RestaurantScheduleCodec {

    private static final Set<String> VALID_DAYS = Set.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectMapper objectMapper;

    /** Never throws - a missing/corrupt blob (e.g. an older record that predates this feature) just reads back as "no schedule set". */
    public List<DayScheduleDto> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DayScheduleDto>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Validates day names (known, no duplicates), that open days carry at least one well-formed
     * slot, that every slot closes after it opens, and that a day's slots don't overlap (or
     * exactly duplicate) each other - e.g. two "09:00-22:00" entries, or "09:00-14:00" plus
     * "13:00-18:00", on the same day.
     */
    public String validateAndSerialize(List<DayScheduleDto> schedule) {
        if (schedule == null) {
            throw new BadRequestException("weeklySchedule is required");
        }
        Set<String> seenDays = new HashSet<>();
        for (DayScheduleDto day : schedule) {
            String dayName = day.day() == null ? null : day.day().toLowerCase();
            if (dayName == null || !VALID_DAYS.contains(dayName)) {
                throw new BadRequestException("weeklySchedule: invalid day '" + day.day() + "'");
            }
            if (!seenDays.add(dayName)) {
                throw new BadRequestException("weeklySchedule: duplicate day '" + dayName + "'");
            }
            if (day.isOpen()) {
                List<TimeSlotDto> slots = day.slots();
                if (slots == null || slots.isEmpty()) {
                    throw new BadRequestException("weeklySchedule: " + dayName + " is marked open but has no time slots");
                }
                List<LocalTime[]> parsedSlots = new ArrayList<>();
                for (TimeSlotDto slot : slots) {
                    LocalTime open = parseTime(dayName, slot.open());
                    LocalTime close = parseTime(dayName, slot.close());
                    if (!open.isBefore(close)) {
                        throw new BadRequestException("weeklySchedule: " + dayName + "'s slot close time must be after its open time");
                    }
                    parsedSlots.add(new LocalTime[]{open, close});
                }
                parsedSlots.sort(Comparator.comparing(pair -> pair[0]));
                for (int i = 1; i < parsedSlots.size(); i++) {
                    LocalTime previousClose = parsedSlots.get(i - 1)[1];
                    LocalTime currentOpen = parsedSlots.get(i)[0];
                    if (currentOpen.isBefore(previousClose)) {
                        throw new BadRequestException("weeklySchedule: " + dayName + "'s time slots overlap - each slot must start at or after the previous one ends");
                    }
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(schedule);
        } catch (Exception e) {
            throw new BadRequestException("weeklySchedule could not be processed");
        }
    }

    private LocalTime parseTime(String dayName, String value) {
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("weeklySchedule: " + dayName + "'s time slots must be in HH:mm format");
        }
    }
}
