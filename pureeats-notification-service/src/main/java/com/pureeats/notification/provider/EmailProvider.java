package com.pureeats.notification.provider;

import com.pureeats.notification.dto.NotificationResult;

/**
 * Infrastructure boundary for "actually send an email". {@code EmailNotificationService} depends
 * only on this - swapping Gmail SMTP for AWS SES/SendGrid later means writing one new class here,
 * never touching auth/order/rating business logic.
 */
public interface EmailProvider {
    NotificationResult send(String to, String subject, String htmlBody, String textBody);
}
