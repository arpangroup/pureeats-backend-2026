package com.pureeats.order.dto;

import java.time.LocalDateTime;

public record OrderStatusLogResponse(
        Long id,
        String fromStatus,
        String toStatus,
        String actorType,
        Long actorUserId,
        String actorName,
        String note,
        LocalDateTime createdAt
) {
}
