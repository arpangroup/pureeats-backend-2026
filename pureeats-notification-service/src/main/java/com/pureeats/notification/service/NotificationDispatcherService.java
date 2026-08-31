package com.pureeats.notification.service;

import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Routes a {@link NotificationRequest} to the sender registered for its channel. */
@Service
@Slf4j
public class NotificationDispatcherService implements NotificationService {

    private final Map<NotificationChannel, ChannelNotificationSender> sendersByChannel;

    public NotificationDispatcherService(List<ChannelNotificationSender> senders) {
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(ChannelNotificationSender::channel, Function.identity()));
        log.debug("Registered notification senders for channels {}", sendersByChannel.keySet());
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        // Never log request.params()/body content here - it may carry an OTP for OTP-type notifications.
        ChannelNotificationSender sender = sendersByChannel.get(request.channel());
        if (sender == null) {
            log.warn("No sender registered for channel {} (notification type {})", request.channel(), request.type());
            return NotificationResult.failure("No sender registered for channel " + request.channel());
        }
        log.debug("Dispatching {} notification on channel {}", request.type(), request.channel());
        return sender.send(request);
    }
}
