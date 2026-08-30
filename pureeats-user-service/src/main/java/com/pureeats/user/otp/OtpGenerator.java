package com.pureeats.user.otp;

/** Generates the OTP the user actually types in - never the hash, never persisted by this class. */
public interface OtpGenerator {
    String generate(int length);
}
