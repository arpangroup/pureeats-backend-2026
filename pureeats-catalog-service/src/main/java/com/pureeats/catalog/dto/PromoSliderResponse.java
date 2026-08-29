package com.pureeats.catalog.dto;

import java.util.List;

public record PromoSliderResponse(Long id, String name, Integer positionId, Integer size, List<SlideResponse> slides) {
}
