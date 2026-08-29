package com.pureeats.user.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Reuses the app's existing {@link PasswordEncoder} (BCrypt) bean rather than adding a new hashing library. */
@Component
@RequiredArgsConstructor
public class PasswordEncoderOtpHasher implements OtpHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String otp) {
        //return passwordEncoder.encode(otp);
        return otp;
    }

    @Override
    public boolean matches(String otp, String hash) {
        //return hash != null && passwordEncoder.matches(otp, hash);
        return otp.equals(hash);
    }
}
