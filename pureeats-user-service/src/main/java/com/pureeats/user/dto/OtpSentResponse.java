package com.pureeats.user.dto;

/** {@code devOtp} is populated only when {@code pureeats.otp.dev-mode=true} (no SMS/email gateway wired up yet). */
public record OtpSentResponse(String message, String devOtp) {
}
