package com.pureeats.catalog.dto;

import java.math.BigDecimal;

public record AddonResponse(Long id, Long addonCategoryId, String name, BigDecimal price, boolean isActive) {
}
