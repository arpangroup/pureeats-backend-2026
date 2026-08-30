package com.pureeats.catalog.dto;

import java.time.LocalDateTime;

public record RestaurantAuditLogResponse(
        Long id,
        String fieldName,
        String oldValue,
        String newValue,
        Long updatedBy,
        String updatedByName,
        LocalDateTime updatedAt
) {
}
