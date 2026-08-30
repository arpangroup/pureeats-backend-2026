package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record RestaurantCategoryRequest(@NotBlank String name, Boolean isActive) {
}
