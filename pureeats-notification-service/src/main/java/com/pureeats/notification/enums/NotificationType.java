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
    ORDER_STATUS_UPDATE,
    /** A brand-new order was just placed - sent to every ADMIN/SUPER_ADMIN/EMPLOYEE user (not the order's own customer), unconditionally on PUSH+IN_APP, regardless of NotificationRoutingService's per-role config (see OrderNotificationService#notifyAdminsOfNewOrder). Distinct from ORDER_STATUS_UPDATE since this isn't a transition on an existing order - it's the platform-operations "something needs your attention" alert. */
    NEW_ORDER,
    /** One-off "send test notification" triggered from the admin panel (Settings → Push Notifications) - PUSH doesn't render a template for any type (title/body come straight from {@code params}), so this is safe there. Never dispatch this type on EMAIL/SMS through the normal templated path - there's no template file for it and template resolution would silently fall back to the OTP template instead; the admin test-email endpoint calls {@code EmailProvider} directly for that reason. */
    TEST
}
