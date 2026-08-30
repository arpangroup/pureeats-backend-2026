package com.pureeats.user.dto;

import com.pureeats.user.enums.BlockStatus;
import com.pureeats.user.enums.BlockType;

import java.time.LocalDateTime;

/** {@code value} is returned unmasked - unlike other admin views, seeing exactly what's blocked
 * (an IP/device id/email/phone/user id) is the entire point of this endpoint. */
public record SecurityBlockEntryResponse(
        Long id,
        BlockType blockType,
        String value,
        String reason,
        BlockStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String createdBy
) {
}
