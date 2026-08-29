package com.pureeats.user.entity;

import com.pureeats.user.enums.BlockStatus;
import com.pureeats.user.enums.BlockType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One row = one blocked IP/device/email/phone/user, optionally time-limited. */
@Entity
@Table(name = "security_blocklist", indexes = {
        @Index(name = "idx_security_blocklist_type_value", columnList = "block_type, block_value")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityBlockEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 16)
    private BlockType blockType;

    // Named block_value, not value - "value" is a reserved word on H2/some SQL dialects.
    @Column(name = "block_value", nullable = false)
    private String value;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BlockStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** {@code null} means a permanent block. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by")
    private String createdBy;
}
