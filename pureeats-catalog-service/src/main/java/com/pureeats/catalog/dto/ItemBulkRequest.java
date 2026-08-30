package com.pureeats.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ItemBulkRequest(
        @NotEmpty @Valid List<AdminItemCreateRequest> items
) {
}
