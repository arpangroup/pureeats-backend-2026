package com.pureeats.notification.service;

import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The zero-config channel - just logs. Useful in local dev (see it fire without any provider setup)
 * and as a deliberately-always-available fan-out target alongside real channels, e.g. so an admin
 * can add a console trace for LOGIN_OTP without needing Twilio/SMTP credentials.
 */
@Service
@Slf4j
public class ConsoleNotificationSender implements ChannelNotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.CONSOLE;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        String title = String.valueOf(request.params().getOrDefault("title", request.type().name()));
        String body = String.valueOf(request.params().getOrDefault("body", ""));
        log.info("[console-notification] type={} userId={} destination={} title='{}' body='{}'",
                request.type(), request.userId(), request.destination(), title, body);
        return NotificationResult.success("console");
    }
}
