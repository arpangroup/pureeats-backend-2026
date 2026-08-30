package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.PasswordResetOtp;
import com.pureeats.domain.entity.User;
import com.pureeats.user.dto.OtpSentResponse;
import com.pureeats.user.repository.PasswordResetOtpRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Email-based password reset. No email gateway is wired up yet for this legacy flow. */
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
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        String code = generateCode();
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setUserId(user.getId().intValue());
        otp.setCode(code);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setUpdatedAt(LocalDateTime.now());
        passwordResetOtpRepository.save(otp);

        return new OtpSentResponse("Password reset code sent to your email", devMode ? code : null);
    }

    @Transactional(readOnly = true)
    public void verifyCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        latestValidOtpFor(user, code);
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        latestValidOtpFor(user, code);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private void latestValidOtpFor(User user, String code) {
        PasswordResetOtp latest = passwordResetOtpRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId().intValue())
                .orElseThrow(() -> new BadRequestException("No password reset was requested for this account"));

        if (!latest.getCode().equalsIgnoreCase(code)) {
            throw new BadRequestException("Invalid reset code");
        }
        if (latest.getCreatedAt().isBefore(LocalDateTime.now().minus(CODE_VALIDITY_MINUTES, ChronoUnit.MINUTES))) {
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
