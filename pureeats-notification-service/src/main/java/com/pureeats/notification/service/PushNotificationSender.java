package com.pureeats.notification.service;

import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.dto.FcmAction;
import com.pureeats.notification.dto.FcmPushRequest;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Fans a notification out to every active Firebase device token the recipient has registered -
 * unlike EMAIL/SMS (one fixed {@code destination} per request), PUSH resolves its own destinations
 * from {@code request.userId()} via {@link PushTokenRepository}, since a user can be signed in on
 * several devices at once. Title/body come straight from {@code params} (no template file) since
 * push copy for order-status updates etc. is composed dynamically by the calling service, not
 * fixed per {@link com.pureeats.notification.enums.NotificationType}.
 * <p>
 * {@code params} may additionally carry {@code imageUrl} (String), {@code clickAction} (String -
 * the link opened on click), {@code data} ({@code Map<String,String>}), {@code actions}
 * ({@code List<FcmAction>}) and {@code silent} (Boolean - see {@link
 * com.pureeats.notification.enums.PushDisplayMode#SILENT}) - all optional, since every existing
 * caller only ever sets title/body/category. These are same-JVM values a caller builds directly
 * (never deserialized from JSON at this layer), so they're cast rather than parsed; a caller
 * passing the wrong shape is a programming error, not a runtime input to guard against.
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
        boolean silent = Boolean.TRUE.equals(request.params().get("silent"));
        String title = String.valueOf(request.params().getOrDefault("title", request.type().name()));
        String body = String.valueOf(request.params().getOrDefault("body", ""));
        String category = request.params().get("category") != null ? String.valueOf(request.params().get("category")) : null;
        String imageUrl = stringParam(request, "imageUrl");
        String clickAction = stringParam(request, "clickAction");
        Map<String, String> data = new java.util.HashMap<>();
        if (category != null) data.put("type", category);
        if (request.params().get("data") instanceof Map<?, ?> extra) {
            extra.forEach((k, v) -> data.put(String.valueOf(k), String.valueOf(v)));
        }
        List<FcmAction> actions = request.params().get("actions") instanceof List<?> raw
                ? raw.stream().filter(FcmAction.class::isInstance).map(FcmAction.class::cast).toList()
                : List.of();

        List<PushToken> tokens = pushTokenRepository.findByUserIdAndIsActiveTrue(request.userId().intValue());
        if (tokens.isEmpty()) {
            log.debug("No active push tokens for user {} - skipping PUSH for {}", request.userId(), request.type());
            return NotificationResult.failure("No active push tokens registered for this user");
        }
        for (PushToken token : tokens) {
            FcmPushRequest fcmRequest = silent
                    ? FcmPushRequest.silent(token.getToken(), null, data)
                    : FcmPushRequest.visible(token.getToken(), null, title, body, imageUrl, data, clickAction, actions);
            fcmSender.send(fcmRequest);
        }
        log.info("Dispatched {} PUSH notification ({}) to {} device(s) for user {}", silent ? "silent" : "visible", request.type(), tokens.size(), request.userId());
        return NotificationResult.success(tokens.size() + " device(s)");
    }

    private String stringParam(NotificationRequest request, String key) {
        Object value = request.params().get(key);
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : null;
    }
}
