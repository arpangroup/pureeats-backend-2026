package com.pureeats.domain.entity;

import com.pureeats.domain.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    // Not NOT-NULL: OTP-only accounts (see UserProvisioningService) never get a password. MySQL's
    // default (non-strict) sql_mode silently accepted NULL here before, masking that this was
    // ever a problem - a stricter engine (H2/Postgres/MySQL strict mode) rejects it outright.
    @Column(name = "password")
    private String password;

    @Column(name = "remember_token")
    private String rememberToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Lob
    @Column(name = "auth_token")
    private String authToken;

    @Column(name = "phone", unique = true)
    private String phone;

    @Column(name = "is_active")
    private String isActive;

    @Column(name = "default_address_id")
    private Integer defaultAddressId;

    @Column(name = "delivery_pin")
    private String deliveryPin;

    @Column(name = "delivery_guy_detail_id")
    private Integer deliveryGuyDetailId;

    @Column(name = "photo")
    private String photo;

    // --- OTP-based auth additions below. `isActive`/`emailVerifiedAt` above are the legacy
    // fields and are left untouched for backward compatibility with the password/legacy-OTP flows. ---

    /**
     * Independently-tracked phone verification, mirroring the legacy {@code emailVerifiedAt}
     * column above. {@code null} means not verified - there is no separate boolean flag, since a
     * nullable timestamp is already an unambiguous single source of truth for "is it verified".
     */
    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    /** New, richer status the OTP auth flow enforces; independent of the legacy {@code isActive} string. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 24)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    /** {@code null} while {@code accountStatus == BLOCKED} (indefinite); set for {@code TEMPORARILY_LOCKED}. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "lock_reason")
    private String lockReason;

    public boolean isEmailVerified() {
        return getEmailVerifiedAt() != null;
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }
}
