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
        String tags
) {
}
