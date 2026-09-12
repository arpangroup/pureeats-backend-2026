package com.pureeats.user.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.exception.ApiException;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ConflictException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.User;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.service.NotificationService;
import com.pureeats.user.config.AuthSecurityProperties;
import com.pureeats.user.dto.LoginChallengeResponse;
import com.pureeats.user.dto.UserResponse;
import com.pureeats.user.entity.OtpChallenge;
import com.pureeats.user.enums.AuthenticationMethod;
import com.pureeats.user.otp.OtpChallengeService;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.security.metadata.RequestMetadata;
import com.pureeats.user.security.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lets a signed-in user change their own phone number or email - both double as OTP-login
 * identifiers, so (per product decision) a change only takes effect once the *new* destination is
 * itself OTP-verified, using the same {@link OtpChallengeService} the login/signup flow already
 * runs on (same fixed test code today - see the {@code "123456"} placeholder in
 * OtpChallengeService). Deliberately its own small service rather than folding into
 * {@link AuthenticationService}: that service's helpers are shaped around issuing a *session* on
 * success, where this only ever updates one column on an already-authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileContactChangeService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final OtpChallengeService otpChallengeService;
    private final NotificationService notificationService;
    private final AuthSecurityProperties properties;
    private final RateLimiter rateLimiter;
    private final com.pureeats.media.storage.MediaUrlResolver mediaUrlResolver;

    @Transactional
    public LoginChallengeResponse requestPhoneChange(Long userId, String newPhone, RequestMetadata metadata) {
        if (userRepository.existsByPhone(newPhone)) {
            throw new ConflictException("PHONE_ALREADY_IN_USE", "This phone number is already linked to another account.");
        }
        return issueChallenge(AuthenticationMethod.PHONE, newPhone, NotificationType.PHONE_VERIFICATION, userId, metadata);
    }

    @Transactional
    public LoginChallengeResponse requestEmailChange(Long userId, String newEmail, RequestMetadata metadata) {
        if (userRepository.existsByEmail(newEmail)) {
            throw new ConflictException("EMAIL_ALREADY_IN_USE", "This email is already linked to another account.");
        }
        return issueChallenge(AuthenticationMethod.EMAIL, newEmail, NotificationType.EMAIL_VERIFICATION, userId, metadata);
    }

    @Transactional
    public UserResponse confirmPhoneChange(Long userId, String challengeId, String otp) {
        OtpChallenge challenge = verifyOwnChallenge(userId, challengeId, otp, AuthenticationMethod.PHONE);
        User user = findUserOrThrow(userId);
        if (userRepository.existsByPhone(challenge.getDestination())) {
            throw new ConflictException("PHONE_ALREADY_IN_USE", "This phone number is already linked to another account.");
        }
        user.setPhone(challenge.getDestination());
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Phone number changed for user {}", userId);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(user.getPhoto()));
    }

    @Transactional
    public UserResponse confirmEmailChange(Long userId, String challengeId, String otp) {
        OtpChallenge challenge = verifyOwnChallenge(userId, challengeId, otp, AuthenticationMethod.EMAIL);
        User user = findUserOrThrow(userId);
        if (userRepository.existsByEmail(challenge.getDestination())) {
            throw new ConflictException("EMAIL_ALREADY_IN_USE", "This email is already linked to another account.");
        }
        user.setEmail(challenge.getDestination());
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Email changed for user {}", userId);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(user.getPhoto()));
    }

    private OtpChallenge verifyOwnChallenge(Long userId, String challengeId, String otp, AuthenticationMethod expectedMethod) {
        OtpChallenge challenge = otpChallengeService.verify(challengeId, otp);
        if (!userId.equals(challenge.getUserId()) || challenge.getAuthenticationMethod() != expectedMethod) {
            log.warn("Contact-change challenge {} does not belong to user {} / method {}", challengeId, userId, expectedMethod);
            throw new BadRequestException("CHALLENGE_NOT_FOUND", "This verification session was not found or has expired.");
        }
        return challenge;
    }

    private LoginChallengeResponse issueChallenge(AuthenticationMethod method, String destination, NotificationType purpose,
                                                    Long userId, RequestMetadata metadata) {
        AuthSecurityProperties.Otp otpProperties = properties.getOtp();
        rateLimiter.enforce("profile-contact-change:dest:" + destination, otpProperties.getMaxRequestsPerDestinationPerHour(),
                Duration.ofHours(1), "You have requested too many OTPs. Please try again later.");
        rateLimiter.enforce("profile-contact-change:ip:" + metadata.ipAddress(), otpProperties.getMaxRequestsPerIpPerHour(),
                Duration.ofHours(1), "Too many OTP requests from this network. Please try again later.");

        OtpChallengeService.GeneratedOtp generated = otpChallengeService.createChallenge(method, destination, purpose, userId, metadata);
        User user = findUserOrThrow(userId);

        NotificationChannel channel = method == AuthenticationMethod.PHONE ? NotificationChannel.SMS : NotificationChannel.EMAIL;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("otp", generated.plainOtp());
        params.put("expiryMinutes", otpProperties.getExpiryMinutes());
        params.put("userName", user.getName() != null && !user.getName().isBlank() ? user.getName() : "there");

        CompletableFuture<NotificationResult> future = notificationService.sendAsync(
                new NotificationRequest(purpose, channel, destination, userId, params));
        NotificationResult result = awaitDelivery(future, purpose);
        if (!result.success()) {
            log.error("Failed to deliver contact-change OTP for challenge {} (purpose={})", generated.challenge().getChallengeId(), purpose);
            throw new ApiException(502, "NOTIFICATION_DELIVERY_FAILED", "Unable to send the verification code right now. Please try again.");
        }

        long expiresIn = Duration.between(LocalDateTime.now(), generated.challenge().getExpiresAt()).toSeconds();
        return new LoginChallengeResponse(true, "OTP sent successfully.", generated.challenge().getChallengeId(),
                PiiMaskUtil.maskDestination(destination), Math.max(0, expiresIn), otpProperties.getResendCooldownSeconds());
    }

    private NotificationResult awaitDelivery(CompletableFuture<NotificationResult> future, NotificationType purpose) {
        try {
            return future.get(properties.getOtp().getSendTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.info("{} notification is taking longer than expected - responding now, delivery continues in the background", purpose);
            return NotificationResult.success("pending-async");
        } catch (Exception e) {
            log.warn("{} notification dispatch failed: {}", purpose, e.getMessage());
            return NotificationResult.failure(e.getMessage());
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
