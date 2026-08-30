package com.pureeats.user.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Cryptographically secure, numeric-only, fixed-length OTP generator. */
@Component
public class SecureOtpGenerator implements OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate(int length) {
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("OTP length must be between 4 and 10 digits");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
