package com.pureeats.notification.enums;

/**
 * Drives which template is rendered ({@code {channel}/{type}.html|txt} on the classpath) -
 * adding a new type never requires touching the auth business logic that requests it.
 */
public enum NotificationType {
    LOGIN_OTP,
    SIGNUP_OTP,
    PASSWORD_RESET_OTP,
    EMAIL_VERIFICATION,
    PHONE_VERIFICATION
}
