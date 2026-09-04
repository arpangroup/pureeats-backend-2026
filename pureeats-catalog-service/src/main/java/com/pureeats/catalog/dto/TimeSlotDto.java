package com.pureeats.catalog.dto;

/** One open window within a day, e.g. {@code {"open": "09:00", "close": "22:00"}}. */
public record TimeSlotDto(String open, String close) {
}
