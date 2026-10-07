package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One online/offline change for a delivery partner - the history the rider app shows under Activity,
 * so a partner can see e.g. that the inactivity scheduler took them offline. {@code reason} is one of
 * DeliveryGuyDetail.OFFLINE_REASON_* (SELF / INACTIVITY / ADMIN); plain string, no CHECK constraint.
 */
@Entity
@Table(name = "rider_status_logs", indexes = @Index(name = "idx_rider_status_logs_user", columnList = "rider_user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class RiderStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "rider_user_id", nullable = false)
    private Long riderUserId;

    @Column(name = "is_online", nullable = false)
    private Boolean isOnline;

    @Column(name = "reason", length = 32)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
