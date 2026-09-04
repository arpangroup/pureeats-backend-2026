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
    PHONE_VERIFICATION,
    /** Generic order-lifecycle notification (placed/accepted/picked up/delivered/cancelled/...) - the specific title/body text is composed by the caller (order-service's {@code OrderNotificationService}) and passed via {@code params}, since the possible transitions and their wording are order-domain knowledge this module doesn't own. */
    ORDER_STATUS_UPDATE
}
