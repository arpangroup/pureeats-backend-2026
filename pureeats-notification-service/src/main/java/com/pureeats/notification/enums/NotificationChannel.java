package com.pureeats.notification.enums;

/**
 * Every channel a notification can go out on. Adding a new channel (e.g. Slack) is: add the
 * constant here, write one {@code ChannelNotificationSender} implementation for it, and (if it
 * needs a swappable provider the way EMAIL/SMS do) one provider interface + config-selected bean -
 * nothing else in the codebase changes, since every caller only ever depends on
 * {@code NotificationService}/{@code NotificationDispatcherService}.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    /** Firebase Cloud Messaging push, delivered to every active {@code PushToken} the recipient has registered. */
    PUSH,
    WHATSAPP,
    /** Persists an in-app {@code Alert} row (powers the notification bell / GET /api/v1/notifications) - the always-on channel most other channels are paired with. */
    IN_APP,
    /** Logs to the server console - the zero-config default for local dev and for any notification type an admin hasn't configured a real channel for yet. */
    CONSOLE
}
