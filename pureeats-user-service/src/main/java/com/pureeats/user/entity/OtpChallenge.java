package com.pureeats.user.entity;

import com.pureeats.domain.entity.PasswordResetOtp;
import com.pureeats.domain.entity.User;
import com.pureeats.user.enums.AuthenticationMethod;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.user.enums.OtpChallengeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single OTP verification attempt, decoupled from {@link User} (userId is nullable - a
 * signup challenge has no user yet). Replaces the legacy {@link PasswordResetOtp} table's role for
 * OTP-based auth - this is the richer, policy-enforced table backing the challenge/verify/resend
 * flow. Lives in pureeats-user-service (not domain) since only this module ever reads/writes it.
 */
@Entity
@Table(name = "otp_challenges", indexes = {
        @Index(name = "idx_otp_challenges_challenge_id", columnList = "challenge_id", unique = true),
        @Index(name = "idx_otp_challenges_user_id", columnList = "user_id"),
        @Index(name = "idx_otp_challenges_destination", columnList = "destination"),
        @Index(name = "idx_otp_challenges_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Opaque, externally-exposed identifier (UUID) - never the numeric primary key. */
    @Column(name = "challenge_id", nullable = false, unique = true, length = 36)
    private String challengeId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_method", nullable = false, length = 16)
    private AuthenticationMethod authenticationMethod;

    /** Raw phone/email this challenge was issued for - masked before ever leaving the API. */
    @Column(name = "destination", nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private NotificationType purpose;

    /** Salted hash of the OTP (via the app's existing {@code PasswordEncoder}) - the plaintext is never stored. */
    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OtpChallengeStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "resend_count", nullable = false)
    private Integer resendCount = 0;

    @Column(name = "max_resend_count", nullable = false)
    private Integer maxResendCount;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** Correlation id of the request that created this challenge, for tracing across audit/notification logs. */
    @Column(name = "request_id", length = 64)
    private String requestId;

    /** Optimistic lock - verify/resend both mutate attempt/resend counters under concurrent requests. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
