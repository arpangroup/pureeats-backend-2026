package com.pureeats.notification.service;

import com.pureeats.notification.config.AsyncNotificationConfig;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

    @Override
    public Map<NotificationChannel, NotificationResult> sendToChannels(NotificationType type, String destination, Long userId,
                                                                          Map<String, Object> params, Set<NotificationChannel> channels) {
        Map<NotificationChannel, NotificationResult> results = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : channels) {
            try {
                results.put(channel, send(new NotificationRequest(type, channel, destination, userId, params)));
            } catch (Exception e) {
                log.warn("Channel {} threw while sending {} notification to user {} - continuing with remaining channels", channel, type, userId, e);
                results.put(channel, NotificationResult.failure(e.getMessage()));
            }
        }
        return results;
    }

    /**
     * The whole method body - including the provider round-trip inside {@link #send} - runs on
     * {@value AsyncNotificationConfig#EXECUTOR_BEAN_NAME}, not the caller's thread, because the
     * proxy Spring wraps this bean in intercepts the call before this method body ever executes.
     * Must be invoked through the injected {@code NotificationService} bean (as every caller does) -
     * calling it via {@code this.sendAsync(...)} from inside this same class would bypass the proxy
     * and run synchronously.
     */
    @Override
    @Async(AsyncNotificationConfig.EXECUTOR_BEAN_NAME)
    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) {
        return CompletableFuture.completedFuture(send(request));
    }

    @Override
    @Async(AsyncNotificationConfig.EXECUTOR_BEAN_NAME)
    public void sendToChannelsAsync(NotificationType type, String destination, Long userId,
                                     Map<String, Object> params, Set<NotificationChannel> channels) {
        sendToChannels(type, destination, userId, params, channels);
    }
}
