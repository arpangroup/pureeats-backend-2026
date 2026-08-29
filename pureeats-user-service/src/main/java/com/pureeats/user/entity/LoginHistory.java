package com.pureeats.user.entity;

import com.pureeats.user.enums.LoginMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Append-only audit trail of login attempts, separate from the live {@link UserSession} table. */
@Entity
@Table(name = "login_history", indexes = {
        @Index(name = "idx_login_history_user_id", columnList = "user_id"),
        @Index(name = "idx_login_history_ip_address", columnList = "ip_address")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_method", nullable = false, length = 16)
    private LoginMethod loginMethod;

    /** "SUCCESS" / "FAILED" - kept as a short string rather than a new enum tied 1:1 to this table. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Lob
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "city", length = 64)
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "failure_reason")
    private String failureReason;
}
