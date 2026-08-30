package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record ItemCategoryRequest(@NotBlank String name) {
}
