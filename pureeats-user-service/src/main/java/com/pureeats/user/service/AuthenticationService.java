package com.pureeats.user.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.exception.ApiException;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ConflictException;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.UnauthorizedException;
import com.pureeats.user.entity.OtpChallenge;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.user.enums.AuthenticationMethod;
import com.pureeats.user.enums.BlockType;
import com.pureeats.user.enums.LoginMethod;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.domain.enums.Role;
import com.pureeats.user.enums.SecurityEventType;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.service.NotificationService;
import com.pureeats.user.config.AuthSecurityProperties;
import com.pureeats.user.dto.AuthTokenResponse;
import com.pureeats.user.dto.LoginChallengeRequest;
import com.pureeats.user.dto.LoginChallengeResponse;
import com.pureeats.user.dto.ResendOtpResponse;
import com.pureeats.user.dto.SignupRequest;
import com.pureeats.user.otp.OtpChallengeService;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.security.JwtTokenProvider;
import com.pureeats.user.security.audit.SecurityEvent;
import com.pureeats.user.security.audit.SecurityEventPublisher;
import com.pureeats.user.security.blocklist.BlocklistService;
import com.pureeats.user.security.device.DeviceService;
import com.pureeats.user.security.device.LoginHistoryRecorder;
import com.pureeats.user.security.metadata.RequestMetadata;
import com.pureeats.user.security.ratelimit.RateLimiter;
import com.pureeats.user.security.session.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the OTP-challenge auth flow: validates the request shape, checks the blocklist and
 * rate limits, delegates OTP lifecycle to {@link OtpChallengeService}, sends the notification,
 * provisions/loads the {@code User}, and on success issues an access token + refresh session.
 * Deliberately thin - every piece of actual logic (OTP rules, notification delivery, blocklists,
 * rate limits, device/session bookkeeping) lives in its own single-responsibility collaborator.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthSecurityProperties properties;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final UserProvisioningService userProvisioningService;
    private final OtpChallengeService otpChallengeService;
    private final NotificationService notificationService;
    private final BlocklistService blocklistService;
    private final RateLimiter rateLimiter;
    private final DeviceService deviceService;
    private final SessionService sessionService;
    private final LoginHistoryRecorder loginHistoryRecorder;
    private final SecurityEventPublisher securityEventPublisher;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginChallengeResponse signup(SignupRequest request, RequestMetadata metadata) {
        roleService.assertCallerNotPrivileged();
        assertNotBlocked(BlockType.IP, metadata.ipAddress());
        assertNotBlocked(BlockType.EMAIL, request.email());
        assertNotBlocked(BlockType.DEVICE, metadata.deviceId());
        enforceOtpRequestRateLimits("signup", request.email(), metadata);

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "Email ID already registered.");
        }

        User user = userProvisioningService.provisionViaEmail(request.email(), request.fullName());
        securityEventPublisher.publish(baseEvent(SecurityEventType.SIGNUP_INITIATED, user.getId(), metadata).build());

        return issueOtpChallenge(AuthenticationMethod.EMAIL, request.email(), NotificationType.SIGNUP_OTP,
                user.getId(), request.fullName(), metadata);
    }

    @Transactional
    public LoginChallengeResponse initiateLogin(LoginChallengeRequest request, RequestMetadata metadata) {
        String destination = validateAndExtractDestination(request);
        BlockType destinationBlockType = request.method() == AuthenticationMethod.PHONE ? BlockType.PHONE : BlockType.EMAIL;

        assertNotBlocked(BlockType.IP, metadata.ipAddress());
        assertNotBlocked(destinationBlockType, destination);
        assertNotBlocked(BlockType.DEVICE, metadata.deviceId());

        AuthSecurityProperties.RateLimit rl = properties.getRateLimit();
        rateLimiter.enforce("otp-login:ip:" + metadata.ipAddress(), rl.getLoginIp().getLimit(),
                Duration.ofSeconds(rl.getLoginIp().getWindowSeconds()), "Too many login attempts. Please try again later.");
        rateLimiter.enforce("otp-login:dest:" + destination, rl.getLoginDestination().getLimit(),
                Duration.ofSeconds(rl.getLoginDestination().getWindowSeconds()), "Too many login attempts for this account. Please try again later.");
        enforceOtpRequestRateLimits("login", destination, metadata);

        Optional<User> existing = request.method() == AuthenticationMethod.PHONE
                ? userRepository.findByPhone(destination)
                : userRepository.findByEmail(destination);
        existing.ifPresent(this::assertAccountUsable);

        Long userId = existing.map(User::getId).orElse(null);
        String userName = existing.map(User::getName).orElse(null);

        securityEventPublisher.publish(baseEvent(SecurityEventType.LOGIN_INITIATED, userId, metadata)
                .metadata("method", request.method().name())
                .build());

        return issueOtpChallenge(request.method(), destination, NotificationType.LOGIN_OTP, userId, userName, metadata);
    }

    @Transactional
    public ResendOtpResponse resendOtp(String challengeId, RequestMetadata metadata) {
        AuthSecurityProperties.RateLimit rl = properties.getRateLimit();
        rateLimiter.enforce("otp-resend:ip:" + metadata.ipAddress(), rl.getResendIp().getLimit(),
                Duration.ofSeconds(rl.getResendIp().getWindowSeconds()), "Too many resend requests. Please try again later.");

        OtpChallengeService.GeneratedOtp generated = otpChallengeService.resend(challengeId);
        OtpChallenge challenge = generated.challenge();

        String userName = challenge.getUserId() != null
                ? userRepository.findById(challenge.getUserId()).map(User::getName).orElse(null)
                : null;
        NotificationResult result = sendOtpNotification(challenge, generated.plainOtp(), userName);
        if (!result.success()) {
            throw new ApiException(502, "NOTIFICATION_DELIVERY_FAILED", "Unable to send the verification code right now. Please try again.");
        }

        securityEventPublisher.publish(baseEvent(SecurityEventType.OTP_RESENT, challenge.getUserId(), metadata).build());

        long expiresIn = Duration.between(LocalDateTime.now(), challenge.getExpiresAt()).toSeconds();
        return new ResendOtpResponse(true, "A new OTP has been sent.", Math.max(0, expiresIn), properties.getOtp().getResendCooldownSeconds());
    }

    @Transactional
    public AuthTokenResponse verifyOtp(String challengeId, String otp, RequestMetadata metadata) {
        AuthSecurityProperties.RateLimit rl = properties.getRateLimit();
        rateLimiter.enforce("otp-verify:ip:" + metadata.ipAddress(), rl.getVerifyIp().getLimit(),
                Duration.ofSeconds(rl.getVerifyIp().getWindowSeconds()), "Too many verification attempts. Please try again later.");

        OtpChallenge challenge = otpChallengeService.verify(challengeId, otp);
        User user = resolveOrProvisionUser(challenge);
        assertAccountUsable(user);

        markDestinationVerified(user, challenge.getAuthenticationMethod());
        userRepository.save(user);

        deviceService.recordLogin(user.getId(), metadata);
        Role role = roleService.resolveRole(user.getId());
        SessionService.IssuedSession session = sessionService.createSession(user.getId(), metadata);
        String accessToken = generateAccessToken(user, role);

        LoginMethod loginMethod = challenge.getAuthenticationMethod() == AuthenticationMethod.EMAIL
                ? LoginMethod.EMAIL_OTP : LoginMethod.PHONE_OTP;
        loginHistoryRecorder.record(user.getId(), loginMethod, true, metadata, null);
        securityEventPublisher.publish(baseEvent(SecurityEventType.LOGIN_SUCCESS, user.getId(), metadata)
                .metadata("method", loginMethod.name())
                .build());

        return AuthTokenResponse.of(accessToken, session.rawRefreshToken(), accessTokenExpirySeconds());
    }

    @Transactional
    public AuthTokenResponse refresh(String rawRefreshToken, RequestMetadata metadata) {
        SessionService.RotatedSession rotated = sessionService.rotate(rawRefreshToken, metadata);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token"));
        assertAccountUsable(user);

        Role role = roleService.resolveRole(user.getId());
        String accessToken = generateAccessToken(user, role);

        securityEventPublisher.publish(baseEvent(SecurityEventType.TOKEN_REFRESHED, user.getId(), metadata).build());
        return AuthTokenResponse.of(accessToken, rotated.rawRefreshToken(), accessTokenExpirySeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken, RequestMetadata metadata) {
        sessionService.revoke(rawRefreshToken);
        securityEventPublisher.publish(baseEvent(SecurityEventType.LOGOUT, null, metadata).build());
    }

    @Transactional
    public void logoutAll(Long userId, RequestMetadata metadata) {
        sessionService.revokeAllForUser(userId);
        securityEventPublisher.publish(baseEvent(SecurityEventType.LOGOUT_ALL, userId, metadata).build());
    }

    // --- helpers ---

    private LoginChallengeResponse issueOtpChallenge(AuthenticationMethod method, String destination, NotificationType purpose,
                                                       Long userId, String userName, RequestMetadata metadata) {
        OtpChallengeService.GeneratedOtp generated = otpChallengeService.createChallenge(method, destination, purpose, userId, metadata);
        NotificationResult result = sendOtpNotification(generated.challenge(), generated.plainOtp(), userName);
        if (!result.success()) {
            throw new ApiException(502, "NOTIFICATION_DELIVERY_FAILED", "Unable to send the verification code right now. Please try again.");
        }

        securityEventPublisher.publish(baseEvent(SecurityEventType.OTP_SENT, userId, metadata)
                .metadata("method", method.name())
                .build());

        long expiresIn = Duration.between(LocalDateTime.now(), generated.challenge().getExpiresAt()).toSeconds();
        return new LoginChallengeResponse(true, "OTP sent successfully.", generated.challenge().getChallengeId(),
                PiiMaskUtil.maskDestination(destination), Math.max(0, expiresIn), properties.getOtp().getResendCooldownSeconds());
    }

    private NotificationResult sendOtpNotification(OtpChallenge challenge, String plainOtp, String userName) {
        NotificationChannel channel = challenge.getAuthenticationMethod() == AuthenticationMethod.PHONE
                ? NotificationChannel.SMS : NotificationChannel.EMAIL;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("otp", plainOtp);
        params.put("expiryMinutes", properties.getOtp().getExpiryMinutes());
        params.put("userName", userName != null && !userName.isBlank() ? userName : "there");

        return notificationService.send(new NotificationRequest(
                challenge.getPurpose(), channel, challenge.getDestination(), challenge.getUserId(), params));
    }

    private User resolveOrProvisionUser(OtpChallenge challenge) {
        if (challenge.getUserId() != null) {
            return userRepository.findById(challenge.getUserId())
                    .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "Account not found"));
        }
        // Only reachable for a LOGIN_OTP challenge against a phone/email with no existing account -
        // a SIGNUP_OTP challenge always already has a userId (the user row is created at signup time).
        return challenge.getAuthenticationMethod() == AuthenticationMethod.PHONE
                ? userProvisioningService.provisionViaPhoneOtp(challenge.getDestination(), null)
                : userProvisioningService.provisionViaEmail(challenge.getDestination(), null);
    }

    private void markDestinationVerified(User user, AuthenticationMethod method) {
        LocalDateTime now = LocalDateTime.now();
        if (method == AuthenticationMethod.EMAIL) {
            user.setEmailVerifiedAt(now);
        } else {
            user.setPhoneVerifiedAt(now);
        }
    }

    private String validateAndExtractDestination(LoginChallengeRequest request) {
        if (request.method() == AuthenticationMethod.PHONE) {
            if (request.phone() == null || request.phone().isBlank()) {
                throw new BadRequestException("Phone number is required for PHONE authentication.");
            }
            if (request.countryId() == null) {
                throw new BadRequestException("countryId is required for PHONE authentication.");
            }
            return request.phone();
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Email is required for EMAIL authentication.");
        }
        return request.email();
    }

    private void assertNotBlocked(BlockType type, String value) {
        if (value != null && blocklistService.isBlocked(type, value)) {
            throw new ForbiddenException("BLOCKED", "This request cannot be completed. Please contact support if you believe this is an error.");
        }
    }

    private void enforceOtpRequestRateLimits(String scope, String destination, RequestMetadata metadata) {
        AuthSecurityProperties.Otp otp = properties.getOtp();
        rateLimiter.enforce("otp-requests:" + scope + ":dest:" + destination, otp.getMaxRequestsPerDestinationPerHour(),
                Duration.ofHours(1), "You have requested too many OTPs. Please try again later.");
        rateLimiter.enforce("otp-requests:" + scope + ":ip:" + metadata.ipAddress(), otp.getMaxRequestsPerIpPerHour(),
                Duration.ofHours(1), "Too many OTP requests from this network. Please try again later.");
    }

    private void assertAccountUsable(User user) {
        if (User.STATUS_INACTIVE.equalsIgnoreCase(user.getIsActive())) {
            throw new ForbiddenException("ACCOUNT_DEACTIVATED", "This account has been deactivated");
        }
        AccountStatus status = user.getAccountStatus() != null ? user.getAccountStatus() : AccountStatus.ACTIVE;
        switch (status) {
            case BLOCKED -> throw new ForbiddenException("ACCOUNT_BLOCKED",
                    user.getLockReason() != null ? user.getLockReason() : "This account has been blocked.");
            case DISABLED -> throw new ForbiddenException("ACCOUNT_DISABLED", "This account has been disabled.");
            case TEMPORARILY_LOCKED -> {
                if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                    throw new ForbiddenException("ACCOUNT_LOCKED", "This account is temporarily locked. Please try again later.");
                }
                user.setAccountStatus(AccountStatus.ACTIVE);
                user.setLockedAt(null);
                user.setLockedUntil(null);
                user.setLockReason(null);
                userRepository.save(user);
            }
            case ACTIVE -> { /* nothing to do */ }
        }
    }

    private String generateAccessToken(User user, Role role) {
        Long deliveryGuyDetailId = (role == Role.DELIVERY && user.getDeliveryGuyDetailId() != null)
                ? user.getDeliveryGuyDetailId().longValue()
                : null;
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(), role, deliveryGuyDetailId);
        return jwtTokenProvider.generateToken(principal, accessTokenExpirySeconds() * 1000L);
    }

    private long accessTokenExpirySeconds() {
        return properties.getSession().getAccessTokenExpiryMinutes() * 60L;
    }

    private SecurityEvent.Builder baseEvent(SecurityEventType type, Long userId, RequestMetadata metadata) {
        return SecurityEvent.builder(type)
                .userId(userId)
                .requestId(metadata.requestId())
                .ipAddress(metadata.ipAddress())
                .userAgent(metadata.userAgent())
                .deviceId(metadata.deviceId());
    }
}
