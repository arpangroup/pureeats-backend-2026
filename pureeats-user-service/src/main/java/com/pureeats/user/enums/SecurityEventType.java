package com.pureeats.user.enums;

/** What happened, for the {@code audit_logs} table / {@code SecurityEventPublisher}. */
public enum SecurityEventType {
    SIGNUP_INITIATED,
    SIGNUP_SUCCESS,
    LOGIN_INITIATED,
    OTP_SENT,
    OTP_VERIFICATION_SUCCESS,
    OTP_VERIFICATION_FAILED,
    OTP_RESENT,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    LOGOUT_ALL,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    ACCOUNT_BLOCKED,
    ACCOUNT_UNBLOCKED,
    EMAIL_VERIFIED,
    PHONE_VERIFIED,
    TOKEN_REFRESHED,
    TOKEN_REVOKED,
    RATE_LIMIT_EXCEEDED,
    BLOCKED_REQUEST_REJECTED
}
