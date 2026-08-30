package com.pureeats.user.security.blocklist;

import com.pureeats.user.enums.BlockType;

import java.time.LocalDateTime;

/** Checked before every sensitive auth operation (initiate/verify/resend, and login itself). */
public interface BlocklistService {

    boolean isBlocked(BlockType type, String value);

    /** {@code expiresAt == null} means a permanent block. */
    void block(BlockType type, String value, String reason, LocalDateTime expiresAt, String createdBy);
}
