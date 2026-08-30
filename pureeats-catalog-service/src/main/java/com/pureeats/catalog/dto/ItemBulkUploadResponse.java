package com.pureeats.catalog.dto;

import java.util.List;

public record ItemBulkUploadResponse(
        int totalRows,
        int successCount,
        int failureCount,
        List<ItemBulkRowResult> results
) {
}
