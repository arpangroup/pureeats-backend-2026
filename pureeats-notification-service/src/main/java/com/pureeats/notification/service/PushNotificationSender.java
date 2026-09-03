package com.pureeats.notification.service;

import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fans a notification out to every active Firebase device token the recipient has registered -
 * unlike EMAIL/SMS (one fixed {@code destination} per request), PUSH resolves its own destinations
 * from {@code request.userId()} via {@link PushTokenRepository}, since a user can be signed in on
 * several devices at once. Title/body come straight from {@code params} (no template file) since
 * push copy for order-status updates etc. is composed dynamically by the calling service, not
 * fixed per {@link com.pureeats.notification.enums.NotificationType}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationSender implements ChannelNotificationSender {

    private final PushTokenRepository pushTokenRepository;
    private final FcmSender fcmSender;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResult send(NotificationRequest request) {
        if (request.userId() == null) {
            log.warn("Cannot send PUSH notification for type {} - no userId on the request", request.type());
            return NotificationResult.failure("No userId to resolve push tokens for");
        }
        String title = String.valueOf(request.params().getOrDefault("title", request.type().name()));
        String body = String.valueOf(request.params().getOrDefault("body", ""));
        String category = request.params().get("category") != null ? String.valueOf(request.params().get("category")) : null;

        List<PushToken> tokens = pushTokenRepository.findByUserIdAndIsActiveTrue(request.userId().intValue());
        if (tokens.isEmpty()) {
            log.debug("No active push tokens for user {} - skipping PUSH for {}", request.userId(), request.type());
            return NotificationResult.failure("No active push tokens registered for this user");
        }
        for (PushToken token : tokens) {
            fcmSender.send(token.getToken(), title, body, category);
        }
        log.info("Dispatched PUSH notification ({}) to {} device(s) for user {}", request.type(), tokens.size(), request.userId());
        return NotificationResult.success(tokens.size() + " device(s)");
    }
}
