package com.pureeats.notification.dto;

import java.time.LocalDateTime;

public record AlertResponse(Long id, String data, boolean isRead, LocalDateTime createdAt) {
}
