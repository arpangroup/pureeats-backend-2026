package com.pureeats.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitRatingRequest(
        @NotNull Long orderId,
        @NotNull RateableType rateableType,
        @NotNull Long rateableId,
        @Min(1) @Max(5) int rating,
        String comment,
        /**
         * Compliment chips ("Polite", "On time"...). The customer app sends a JSON array; older clients
         * sent a comma-separated string - both are accepted (declared {@code Object} so neither shape is
         * rejected at deserialization) and normalized by {@link #tagsAsString()}.
         */
        Object tags
) {
    /** Tags joined as "a,b,c" (how they're stored), or null when none. */
    public String tagsAsString() {
        if (tags == null) return null;
        java.util.stream.Stream<?> parts = tags instanceof java.util.Collection<?> c ? c.stream() : java.util.Arrays.stream(tags.toString().split(","));
        String joined = parts.map(Object::toString).map(String::trim).filter(t -> !t.isEmpty())
                .collect(java.util.stream.Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }
}
