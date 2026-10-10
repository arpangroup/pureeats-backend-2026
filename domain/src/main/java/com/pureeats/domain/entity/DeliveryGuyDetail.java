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

    // --- Sign-up details (KYC) and admin approval. Plain strings, not enum columns (stale CHECK constraints). ---

    /** PENDING / APPROVED / REJECTED. Null = created before approvals existed (or by an admin) - treated as approved. */
    @Column(name = "approval_status", length = 16)
    private String approvalStatus;

    /** Shown to the partner when an admin rejects the application. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "approval_updated_at")
    private LocalDateTime approvalUpdatedAt;

    @Column(name = "approval_updated_by")
    private Long approvalUpdatedBy;

    @Column(name = "license_number", length = 32)
    private String licenseNumber;

    /** AADHAAR or PAN. */
    @Column(name = "id_proof_type", length = 16)
    private String idProofType;

    @Column(name = "id_proof_number", length = 32)
    private String idProofNumber;

    /** 12 digits. Both Aadhaar and PAN are required now; older partners may have only one, in id_proof_* above. */
    @Column(name = "aadhaar_number", length = 16)
    private String aadhaarNumber;

    /** e.g. ABCDE1234F. */
    @Column(name = "pan_number", length = 16)
    private String panNumber;

    /** Aadhaar on file - the new column, else the single legacy ID proof when it was an Aadhaar. */
    public String effectiveAadhaar() {
        if (aadhaarNumber != null && !aadhaarNumber.isBlank()) return aadhaarNumber;
        return "AADHAAR".equalsIgnoreCase(idProofType) ? idProofNumber : null;
    }

    /** PAN on file - the new column, else the single legacy ID proof when it was a PAN. */
    public String effectivePan() {
        if (panNumber != null && !panNumber.isBlank()) return panNumber;
        return "PAN".equalsIgnoreCase(idProofType) ? idProofNumber : null;
    }

    /** BIKE, CYCLE or EV. */
    @Column(name = "vehicle_type", length = 16)
    private String vehicleType;

    /** BANK or UPI - where earnings are paid out. */
    @Column(name = "payout_method", length = 8)
    private String payoutMethod;

    @Column(name = "bank_account_holder", length = 128)
    private String bankAccountHolder;

    @Column(name = "bank_account_number", length = 32)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 16)
    private String bankIfsc;

    @Column(name = "upi_id", length = 64)
    private String upiId;

    public static final String APPROVAL_PENDING = "PENDING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";

    /** Only approved partners can go online, see or accept orders. Legacy/admin-created rows (null) count as approved. */
    public boolean isApproved() {
        return approvalStatus == null || APPROVAL_APPROVED.equals(approvalStatus);
    }

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
