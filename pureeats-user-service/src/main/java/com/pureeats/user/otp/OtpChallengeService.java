package com.pureeats.user.otp;

import com.pureeats.domain.common.exception.ApiException;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.InvalidOtpException;
import com.pureeats.domain.common.exception.TooManyRequestsException;
import com.pureeats.user.entity.OtpChallenge;
import com.pureeats.user.enums.AuthenticationMethod;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.user.enums.OtpChallengeStatus;
import com.pureeats.user.config.AuthSecurityProperties;
import com.pureeats.user.repository.OtpChallengeRepository;
import com.pureeats.user.security.metadata.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Owns the full lifecycle of one {@link OtpChallenge} row: creation, resend (with cooldown/limit
 * enforcement) and verification (with attempt-count lockout). Deliberately knows nothing about
 * notifications, tokens, sessions or the {@code User} table - {@code AuthenticationService}
 * orchestrates those around it. All reads/writes to a given challenge go through a pessimistic
 * row lock ({@link OtpChallengeRepository#findWithLockByChallengeId}) so two concurrent verify (or
 * resend) calls for the same challenge can never both succeed or corrupt the attempt counter.
 */
@Service
@RequiredArgsConstructor
public class OtpChallengeService {

    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final AuthSecurityProperties properties;

    /** {@code plainOtp} exists only transiently, in memory, on its way to the notification service. */
    public record GeneratedOtp(OtpChallenge challenge, String plainOtp) {
    }

    @Transactional
    public GeneratedOtp createChallenge(AuthenticationMethod method, String destination, NotificationType purpose,
                                         Long userId, RequestMetadata metadata) {
        AuthSecurityProperties.Otp otpProperties = properties.getOtp();
        String plainOtp = otpGenerator.generate(otpProperties.getLength());
        LocalDateTime now = LocalDateTime.now();

        OtpChallenge challenge = new OtpChallenge();
        challenge.setChallengeId(UUID.randomUUID().toString());
        challenge.setUserId(userId);
        challenge.setAuthenticationMethod(method);
        challenge.setDestination(destination);
        challenge.setPurpose(purpose);
        challenge.setOtpHash(otpHasher.hash(plainOtp));
        challenge.setStatus(OtpChallengeStatus.PENDING);
        challenge.setExpiresAt(now.plusMinutes(otpProperties.getExpiryMinutes()));
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(otpProperties.getMaxAttempts());
        challenge.setResendCount(0);
        challenge.setMaxResendCount(otpProperties.getMaxResends());
        challenge.setLastSentAt(now);
        challenge.setCreatedAt(now);
        challenge.setUpdatedAt(now);
        challenge.setIpAddress(metadata.ipAddress());
        challenge.setDeviceId(metadata.deviceId());
        challenge.setRequestId(metadata.requestId());

        otpChallengeRepository.save(challenge);
        return new GeneratedOtp(challenge, plainOtp);
    }

    /**
     * {@code REQUIRES_NEW} + {@code noRollbackFor}: verify/resend both mutate the challenge row
     * (attempt count, lock/expiry status) and then routinely throw a "this attempt failed" business
     * exception - by default Spring would roll back that very mutation along with the exception,
     * silently undoing the attempt counter on every wrong guess. Running in its own transaction
     * that commits regardless of the outcome keeps the counter correct independent of whatever the
     * caller's (much larger) transaction later does with the exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public GeneratedOtp resend(String challengeId) {
        OtpChallenge challenge = lockedChallenge(challengeId);
        assertResendable(challenge);

        AuthSecurityProperties.Otp otpProperties = properties.getOtp();
        String plainOtp = otpGenerator.generate(otpProperties.getLength());
        LocalDateTime now = LocalDateTime.now();

        challenge.setOtpHash(otpHasher.hash(plainOtp));
        challenge.setStatus(OtpChallengeStatus.PENDING);
        challenge.setAttemptCount(0);
        challenge.setExpiresAt(now.plusMinutes(otpProperties.getExpiryMinutes()));
        challenge.setResendCount(challenge.getResendCount() + 1);
        challenge.setLastSentAt(now);
        challenge.setUpdatedAt(now);

        otpChallengeRepository.save(challenge);
        return new GeneratedOtp(challenge, plainOtp);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public OtpChallenge verify(String challengeId, String otp) {
        OtpChallenge challenge = lockedChallenge(challengeId);
        LocalDateTime now = LocalDateTime.now();

        switch (challenge.getStatus()) {
            case VERIFIED -> throw new BadRequestException("ALREADY_VERIFIED", "This code has already been used.");
            case CANCELLED -> throw new BadRequestException("CHALLENGE_CANCELLED", "This verification request is no longer valid. Please start again.");
            case LOCKED -> throw new BadRequestException("OTP_ATTEMPTS_EXCEEDED", "Too many incorrect attempts. Please request a new OTP.");
            default -> { /* PENDING/EXPIRED fall through to the expiry check below */ }
        }

        if (challenge.getStatus() != OtpChallengeStatus.EXPIRED && challenge.getExpiresAt().isBefore(now)) {
            challenge.setStatus(OtpChallengeStatus.EXPIRED);
            challenge.setUpdatedAt(now);
            otpChallengeRepository.save(challenge);
        }
        if (challenge.getStatus() == OtpChallengeStatus.EXPIRED) {
            throw new BadRequestException("OTP_EXPIRED", "The OTP has expired. Please request a new OTP.");
        }

        if (otpHasher.matches(otp, challenge.getOtpHash())) {
            challenge.setStatus(OtpChallengeStatus.VERIFIED);
            challenge.setVerifiedAt(now);
            challenge.setUpdatedAt(now);
            otpChallengeRepository.save(challenge);
            return challenge;
        }

        challenge.setAttemptCount(challenge.getAttemptCount() + 1);
        challenge.setUpdatedAt(now);
        int remaining = challenge.getMaxAttempts() - challenge.getAttemptCount();
        if (remaining <= 0) {
            challenge.setStatus(OtpChallengeStatus.LOCKED);
            otpChallengeRepository.save(challenge);
            throw new BadRequestException("OTP_ATTEMPTS_EXCEEDED", "Too many incorrect attempts. Please request a new OTP.");
        }
        otpChallengeRepository.save(challenge);
        throw new InvalidOtpException("The OTP entered is invalid or incorrect.", remaining);
    }

    private OtpChallenge lockedChallenge(String challengeId) {
        return otpChallengeRepository.findWithLockByChallengeId(challengeId)
                .orElseThrow(() -> new BadRequestException("CHALLENGE_NOT_FOUND", "This verification session was not found or has expired."));
    }

    private void assertResendable(OtpChallenge challenge) {
        if (challenge.getStatus() == OtpChallengeStatus.VERIFIED) {
            throw new BadRequestException("ALREADY_VERIFIED", "This code has already been used.");
        }
        if (challenge.getStatus() == OtpChallengeStatus.CANCELLED) {
            throw new BadRequestException("CHALLENGE_CANCELLED", "This verification request is no longer valid. Please start again.");
        }
        LocalDateTime cooldownEnd = challenge.getLastSentAt().plusSeconds(properties.getOtp().getResendCooldownSeconds());
        if (cooldownEnd.isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("RESEND_COOLDOWN", "Please wait before requesting another OTP.");
        }
        if (challenge.getResendCount() >= challenge.getMaxResendCount()) {
            throw new TooManyRequestsException("MAX_RESENDS_EXCEEDED", "You have reached the maximum number of OTP resend attempts.");
        }
    }
}
