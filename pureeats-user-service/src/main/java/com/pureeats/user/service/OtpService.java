package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.SmsOtp;
import com.pureeats.user.repository.SmsOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * OTP generation/verification. No SMS gateway is wired up yet (Twilio/MSG91/etc. credentials
 * are out of scope for this migration pass) - in dev mode the generated code is returned to
 * the caller instead of being sent, so the login-by-OTP flow is fully testable end-to-end.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_VALIDITY_MINUTES = 10;

    private final SmsOtpRepository smsOtpRepository;

    @Value("${pureeats.otp.dev-mode:true}")
    private boolean devMode;

    @Transactional
    public String generateAndStore(String phone) {
        String otp = String.valueOf(1000 + RANDOM.nextInt(9000));
        SmsOtp entity = new SmsOtp();
        entity.setPhone(phone);
        entity.setOtp(otp);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        smsOtpRepository.save(entity);
        return otp;
    }

    public boolean isDevMode() {
        return devMode;
    }

    @Transactional(readOnly = true)
    public void verify(String phone, String otp) {
        SmsOtp latest = smsOtpRepository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new BadRequestException("No OTP was requested for this phone number"));

        if (!latest.getOtp().equals(otp)) {
            throw new BadRequestException("Invalid OTP");
        }
        if (latest.getCreatedAt().isBefore(LocalDateTime.now().minus(OTP_VALIDITY_MINUTES, ChronoUnit.MINUTES))) {
            throw new BadRequestException("OTP has expired, please request a new one");
        }
    }
}
