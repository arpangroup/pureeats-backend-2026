package com.pureeats.notification.provider;

import com.pureeats.notification.dto.NotificationResult;

/**
 * Infrastructure boundary for "actually send an SMS". No real gateway (Twilio/MSG91) credentials
 * are wired up in this codebase yet - {@code ConsoleSmsProvider} is the active default - but
 * {@code SmsNotificationService} and every auth call site already code against this interface,
 * so adding e.g. {@code TwilioSmsProvider} later is additive and provider-local.
 */
public interface SmsProvider {
    NotificationResult send(String phoneNumber, String message);
}
