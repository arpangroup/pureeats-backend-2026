package com.pureeats.app.cache.dto;

import java.util.List;

public record CacheClearResponse(List<String> clearedCaches) {
}
