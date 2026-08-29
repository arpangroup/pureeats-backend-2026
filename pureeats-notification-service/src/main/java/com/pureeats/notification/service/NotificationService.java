package com.pureeats.notification.service;

import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;

/**
 * The one bean auth/order/rating code depends on to notify a user by email or SMS. No module
 * outside {@code pureeats-notification-service} ever imports {@code EmailProvider}/
 * {@code SmsProvider}/JavaMailSender directly.
 */
public interface NotificationService {
    NotificationResult send(NotificationRequest request);
}
