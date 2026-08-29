package com.pureeats.user.entity;

import com.pureeats.user.enums.SecurityEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Append-only security/activity event log. Written by {@code AuditLogSecurityEventPublisher} -
 * business code never inserts here directly, it publishes a {@code SecurityEvent} instead, so a
 * future Kafka/SNS/SIEM sink can be added without touching a single call site.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_logs_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private SecurityEventType eventType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Lob
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "http_method", length = 8)
    private String httpMethod;

    /** "SUCCESS" / "FAILURE" - kept as a short string, mirrors {@link LoginHistory#getStatus()}. */
    @Column(name = "result", length = 16)
    private String result;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Small non-PII key=value context (e.g. {@code method=EMAIL}), never raw OTPs/tokens/passwords. */
    @Lob
    @Column(name = "metadata")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
