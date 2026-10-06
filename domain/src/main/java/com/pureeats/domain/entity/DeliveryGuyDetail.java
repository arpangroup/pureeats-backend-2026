package com.pureeats.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_guy_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryGuyDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "age")
    private String age;

    @Column(name = "gender")
    private String gender;

    @Column(name = "photo")
    private String photo;

    @Column(name = "description")
    private String description;

    @Column(name = "vehicle_number")
    private String vehicleNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "is_notifiable")
    private Boolean isNotifiable;

    @Column(name = "max_accept_delivery_limit", nullable = false)
    private Integer maxAcceptDeliveryLimit;

    @Column(name = "rating", nullable = false)
    private BigDecimal rating;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_online")
    private Boolean isOnline;

    @Column(name = "last_lat")
    private BigDecimal lastLat;

    @Column(name = "last_lng")
    private BigDecimal lastLng;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * Why the rider last went offline - one of {@link #OFFLINE_REASON_SELF},
     * {@link #OFFLINE_REASON_INACTIVITY} or {@link #OFFLINE_REASON_ADMIN}; null while online (or for
     * rows that predate this column). Plain string rather than an enum column on purpose - Hibernate
     * would otherwise emit a CHECK constraint that goes stale the moment a reason is added (see the
     * users_account_status_check incident).
     */
    @Column(name = "offline_reason", length = 32)
    private String offlineReason;

    /** When {@link #isOnline} last flipped, whoever flipped it. */
    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    public static final String OFFLINE_REASON_SELF = "SELF";
    /** Set by RiderInactivityScheduler - no location ping within the configured timeout ("forced stop"). */
    public static final String OFFLINE_REASON_INACTIVITY = "INACTIVITY";
    public static final String OFFLINE_REASON_ADMIN = "ADMIN";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
