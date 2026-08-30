package com.pureeats.user.enums;

/** Lifecycle of a single {@code OtpChallenge} row. */
public enum OtpChallengeStatus {
    PENDING,
    VERIFIED,
    EXPIRED,
    LOCKED,
    CANCELLED
}
