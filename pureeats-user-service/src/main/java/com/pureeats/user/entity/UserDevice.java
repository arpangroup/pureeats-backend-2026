package com.pureeats.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A stable (client-supplied) device id seen for a user, with best-effort UA-derived metadata. */
@Entity
@Table(name = "user_devices", indexes = {
        @Index(name = "idx_user_devices_device_id", columnList = "device_id"),
        @Index(name = "idx_user_devices_user_device", columnList = "user_id, device_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "browser", length = 64)
    private String browser;

    @Column(name = "browser_version", length = 32)
    private String browserVersion;

    @Column(name = "operating_system", length = 64)
    private String operatingSystem;

    @Column(name = "os_version", length = 32)
    private String osVersion;

    @Lob
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
