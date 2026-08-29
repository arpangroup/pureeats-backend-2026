package com.pureeats.notification.dto;

import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;

import java.util.Map;

/**
 * The one shape every caller (auth, order, rating, ...) builds to trigger a notification -
 * callers never know or care whether {@code channel} ends up hitting Gmail SMTP, a console
 * stub, or (later) Twilio/SES. {@code params} feeds the template renderer, e.g. {@code otp},
 * {@code expiryMinutes}, {@code userName}.
 */
public record NotificationRequest(
        NotificationType type,
        NotificationChannel channel,
        String destination,
        Long userId,
        Map<String, Object> params
) {
    public NotificationRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
