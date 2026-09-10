package com.pureeats.catalog.dto;

import java.time.LocalDateTime;

public record SettingHistoryResponse(
        Long id,
        String source,
        String fieldKey,
        String oldValue,
        String newValue,
        Long updatedBy,
        String updatedByName,
        LocalDateTime updatedAt
) {
}
