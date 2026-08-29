package com.pureeats.notification.service;

import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;

/** One implementation per {@link NotificationChannel}; {@link NotificationDispatcherService} routes to these. */
public interface ChannelNotificationSender {
    NotificationChannel channel();

    NotificationResult send(NotificationRequest request);
}
