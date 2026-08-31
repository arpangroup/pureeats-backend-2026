package com.pureeats.user.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.PasswordResetOtp;
import com.pureeats.domain.entity.User;
import com.pureeats.user.dto.OtpSentResponse;
import com.pureeats.user.repository.PasswordResetOtpRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Email-based password reset. No email gateway is wired up yet for this legacy flow. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_VALIDITY_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${pureeats.otp.dev-mode:true}")
    private boolean devMode;

    @Transactional
    public OtpSentResponse sendResetCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Password reset requested for unknown email {}", PiiMaskUtil.maskEmail(email));
                    return new ResourceNotFoundException("No account found with this email");
                });

        String code = generateCode();
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setUserId(user.getId().intValue());
        otp.setCode(code);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setUpdatedAt(LocalDateTime.now());
        passwordResetOtpRepository.save(otp);
        log.info("Password reset code generated for user {}", user.getId());

        return new OtpSentResponse("Password reset code sent to your email", devMode ? code : null);
    }

    @Transactional(readOnly = true)
    public void verifyCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Password reset code verification requested for unknown email {}", PiiMaskUtil.maskEmail(email));
                    return new ResourceNotFoundException("No account found with this email");
                });
        latestValidOtpFor(user, code);
        log.debug("Password reset code verified for user {}", user.getId());
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Password reset completion requested for unknown email {}", PiiMaskUtil.maskEmail(email));
                    return new ResourceNotFoundException("No account found with this email");
                });
        latestValidOtpFor(user, code);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Password reset completed for user {}", user.getId());
    }

    private void latestValidOtpFor(User user, String code) {
        PasswordResetOtp latest = passwordResetOtpRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId().intValue())
                .orElseThrow(() -> {
                    log.warn("No password reset code exists for user {}", user.getId());
                    return new BadRequestException("No password reset was requested for this account");
                });

        if (!latest.getCode().equalsIgnoreCase(code)) {
            log.warn("Incorrect password reset code entered for user {}", user.getId());
            throw new BadRequestException("Invalid reset code");
        }
        if (latest.getCreatedAt().isBefore(LocalDateTime.now().minus(CODE_VALIDITY_MINUTES, ChronoUnit.MINUTES))) {
            log.warn("Expired password reset code presented for user {}", user.getId());
            throw new BadRequestException("Reset code has expired, please request a new one");
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
