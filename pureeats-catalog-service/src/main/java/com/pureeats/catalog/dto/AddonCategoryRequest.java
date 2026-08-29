package com.pureeats.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record AddonCategoryRequest(@NotBlank String name, @NotBlank String type) {
}
