package com.pureeats.notification.dto;

import java.util.Map;

/**
 * Admin-editable, stored as one JSON blob (see {@code NotificationRoutingService}, mirroring
 * catalog-service's {@code AppConfigService}/{@code Setting} pattern). Keys/values are plain
 * strings (matching {@link com.pureeats.notification.enums.NotificationType}/
 * {@link com.pureeats.notification.enums.NotificationChannel}/
 * {@link com.pureeats.notification.enums.NotificationRecipientRole} constant names) rather than the
 * enums themselves, so a stored value from a since-renamed constant fails soft (logged + ignored)
 * instead of breaking JSON deserialization for the whole config.
 *
 * @param extraChannelsByNotificationType Channels to ALSO fire, in addition to the caller-chosen
 *                                        primary channel, for a given {@code NotificationType} - e.g.
 *                                        {@code {"LOGIN_OTP": ["CONSOLE", "PUSH"]}} makes a login OTP
 *                                        also log to console and push a "new sign-in" heads-up,
 *                                        alongside the SMS/EMAIL OTP itself. Empty/missing means "no
 *                                        extra channels", preserving today's single-channel behavior.
 * @param orderStatusChannelsByRole       Channels to use for order-status-change notifications, keyed
 *                                        by recipient role (CUSTOMER/STORE_OWNER/DELIVERY_PARTNER/ADMIN).
 */
public record NotificationRoutingConfig(
        Map<String, java.util.List<String>> extraChannelsByNotificationType,
        Map<String, java.util.List<String>> orderStatusChannelsByRole
) {
}
