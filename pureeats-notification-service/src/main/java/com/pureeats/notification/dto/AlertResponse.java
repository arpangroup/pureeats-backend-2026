package com.pureeats.notification.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record AlertResponse(Long id, Map<String, Object> data, boolean isRead, LocalDateTime createdAt) {
}
