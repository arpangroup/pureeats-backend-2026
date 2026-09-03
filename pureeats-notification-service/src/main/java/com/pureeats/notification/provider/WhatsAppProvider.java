package com.pureeats.notification.provider;

import com.pureeats.notification.dto.NotificationResult;

/**
 * Infrastructure boundary for "actually send a WhatsApp message" - mirrors {@link EmailProvider}/
 * {@link SmsProvider} exactly. No real gateway (e.g. the WhatsApp Business Cloud API, Twilio's
 * WhatsApp API) is wired up yet - {@code ConsoleWhatsAppProvider} is the active default - but
 * {@code WhatsAppNotificationSender} and every caller already code against this interface, so
 * adding a real provider later is a new class + one config-selected bean, nothing else changes.
 */
public interface WhatsAppProvider {
    NotificationResult send(String phoneNumber, String message);
}
