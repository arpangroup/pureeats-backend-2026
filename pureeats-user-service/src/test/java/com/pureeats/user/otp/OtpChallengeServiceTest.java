package com.pureeats.user.otp;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpChallengeServiceTest {

    @Mock
    private OtpChallengeRepository otpChallengeRepository;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock
    private OtpHasher otpHasher;

    private AuthSecurityProperties properties;
    private OtpChallengeService otpChallengeService;

    private RequestMetadata metadata;

    @BeforeEach
    void setUp() {
        properties = new AuthSecurityProperties();
        properties.getOtp().setLength(6);
        properties.getOtp().setExpiryMinutes(10);
        properties.getOtp().setMaxAttempts(3);
        properties.getOtp().setResendCooldownSeconds(30);
        properties.getOtp().setMaxResends(2);
        otpChallengeService = new OtpChallengeService(otpChallengeRepository, otpGenerator, otpHasher, properties);

        metadata = new RequestMetadata("203.0.113.10", "JUnit-Agent", "device-1", "DESKTOP", "Chrome", "120", "Windows", "10", "req-1");

        lenient().when(otpGenerator.generate(anyInt())).thenReturn("123456");
        lenient().when(otpHasher.hash(anyString())).thenReturn("hashed-123456");
        lenient().when(otpChallengeRepository.save(any(OtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createChallengeStoresHashNeverPlaintext() {
        OtpChallengeService.GeneratedOtp generated = otpChallengeService.createChallenge(
                AuthenticationMethod.EMAIL, "john@gmail.com", NotificationType.LOGIN_OTP, 1L, metadata);

        assertEquals("123456", generated.plainOtp());
        assertEquals("hashed-123456", generated.challenge().getOtpHash());
        assertEquals(OtpChallengeStatus.PENDING, generated.challenge().getStatus());
        assertEquals(3, generated.challenge().getMaxAttempts());
    }

    @Test
    void verifySucceedsAndMarksChallengeVerified() {
        OtpChallenge challenge = pendingChallenge();
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));
        when(otpHasher.matches("123456", challenge.getOtpHash())).thenReturn(true);

        OtpChallenge result = otpChallengeService.verify("c-1", "123456");

        assertEquals(OtpChallengeStatus.VERIFIED, result.getStatus());
    }

    @Test
    void verifyWithWrongOtpDecrementsAttemptsAndReportsRemaining() {
        OtpChallenge challenge = pendingChallenge();
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));
        when(otpHasher.matches("000000", challenge.getOtpHash())).thenReturn(false);

        InvalidOtpException ex = assertThrows(InvalidOtpException.class,
                () -> otpChallengeService.verify("c-1", "000000"));

        assertEquals(2, ex.getAttemptsRemaining());
        assertEquals(OtpChallengeStatus.PENDING, challenge.getStatus());
        assertEquals(1, challenge.getAttemptCount());
    }

    @Test
    void verifyLocksChallengeAfterMaxAttemptsExceeded() {
        OtpChallenge challenge = pendingChallenge();
        challenge.setAttemptCount(2); // one more wrong guess reaches maxAttempts=3
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));
        when(otpHasher.matches("000000", challenge.getOtpHash())).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpChallengeService.verify("c-1", "000000"));

        assertEquals("OTP_ATTEMPTS_EXCEEDED", ex.getErrorCode());
        assertEquals(OtpChallengeStatus.LOCKED, challenge.getStatus());
    }

    @Test
    void verifyRejectsAlreadyLockedChallengeWithoutConsumingAnAttempt() {
        OtpChallenge challenge = pendingChallenge();
        challenge.setStatus(OtpChallengeStatus.LOCKED);
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));

        assertThrows(BadRequestException.class, () -> otpChallengeService.verify("c-1", "123456"));
    }

    @Test
    void verifyRejectsExpiredChallenge() {
        OtpChallenge challenge = pendingChallenge();
        challenge.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpChallengeService.verify("c-1", "123456"));

        assertEquals("OTP_EXPIRED", ex.getErrorCode());
    }

    @Test
    void verifyRejectsUnknownChallengeId() {
        when(otpChallengeRepository.findWithLockByChallengeId("missing")).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpChallengeService.verify("missing", "123456"));

        assertEquals("CHALLENGE_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void resendRejectedDuringCooldown() {
        OtpChallenge challenge = pendingChallenge();
        challenge.setLastSentAt(LocalDateTime.now()); // cooldown is 30s, so "now" is still inside it
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));

        assertThrows(TooManyRequestsException.class, () -> otpChallengeService.resend("c-1"));
    }

    @Test
    void resendRejectedAfterMaxResendsReached() {
        OtpChallenge challenge = pendingChallenge();
        challenge.setLastSentAt(LocalDateTime.now().minusMinutes(5));
        challenge.setResendCount(2); // maxResends configured to 2 in setUp()
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));

        assertThrows(TooManyRequestsException.class, () -> otpChallengeService.resend("c-1"));
    }

    @Test
    void resendIssuesNewOtpAndResetsAttempts() {
        OtpChallenge challenge = pendingChallenge();
        challenge.setLastSentAt(LocalDateTime.now().minusMinutes(5));
        challenge.setAttemptCount(2);
        when(otpChallengeRepository.findWithLockByChallengeId("c-1")).thenReturn(Optional.of(challenge));
        when(otpGenerator.generate(6)).thenReturn("654321");
        when(otpHasher.hash("654321")).thenReturn("hashed-654321");

        OtpChallengeService.GeneratedOtp generated = otpChallengeService.resend("c-1");

        assertEquals("654321", generated.plainOtp());
        assertEquals(0, challenge.getAttemptCount());
        assertEquals(1, challenge.getResendCount());
        assertEquals(OtpChallengeStatus.PENDING, challenge.getStatus());
    }

    private OtpChallenge pendingChallenge() {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setChallengeId("c-1");
        challenge.setUserId(1L);
        challenge.setAuthenticationMethod(AuthenticationMethod.EMAIL);
        challenge.setDestination("john@gmail.com");
        challenge.setPurpose(NotificationType.LOGIN_OTP);
        challenge.setOtpHash("hashed-123456");
        challenge.setStatus(OtpChallengeStatus.PENDING);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(3);
        challenge.setResendCount(0);
        challenge.setMaxResendCount(2);
        challenge.setLastSentAt(LocalDateTime.now().minusMinutes(1));
        challenge.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        challenge.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return challenge;
    }
}
