package com.pureeats.notification.service;

import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Routes a {@link NotificationRequest} to the sender registered for its channel. */
@Service
public class NotificationDispatcherService implements NotificationService {

    private final Map<NotificationChannel, ChannelNotificationSender> sendersByChannel;

    public NotificationDispatcherService(List<ChannelNotificationSender> senders) {
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(ChannelNotificationSender::channel, Function.identity()));
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        ChannelNotificationSender sender = sendersByChannel.get(request.channel());
        if (sender == null) {
            return NotificationResult.failure("No sender registered for channel " + request.channel());
        }
        return sender.send(request);
    }
}
