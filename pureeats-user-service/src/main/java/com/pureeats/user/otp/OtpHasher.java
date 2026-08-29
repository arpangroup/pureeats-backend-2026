package com.pureeats.user.otp;

/** Turns a plaintext OTP into the only form that is ever persisted, and checks a guess against it. */
public interface OtpHasher {
    String hash(String otp);

    boolean matches(String otp, String hash);
}
