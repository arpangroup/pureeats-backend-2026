package com.pureeats.catalog.dto;

public record ItemBulkRowResult(
        int index,
        boolean success,
        String message,
        Long itemId
) {
}
