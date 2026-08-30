package com.pureeats.order.dto;

import java.time.LocalDateTime;

public record SupportResponse(Long id, Long orderId, String issue, String message, boolean resolved, LocalDateTime createdAt) {
}
