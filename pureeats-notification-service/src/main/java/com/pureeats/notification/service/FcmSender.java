package com.pureeats.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import com.pureeats.notification.dto.FcmAction;
import com.pureeats.notification.dto.FcmPushRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends real FCM push via the Firebase Admin SDK when {@code pureeats.fcm.credentials-path} points
 * at a valid service-account JSON; otherwise (no property set - the default, since no Firebase
 * project exists yet - or the file is missing/invalid) every send just logs what it would have
 * sent, same as the stub this replaced. Never throws back to the caller
 * ({@link PushNotificationSender}) - a push failure must never block persisting the in-app
 * {@code Alert} or the caller's own transaction (order acceptance, delivery assignment, ...).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender {

    private final ObjectMapper objectMapper;

    @Value("${pureeats.fcm.credentials-path:}")
    private String credentialsPath;

    private volatile boolean initialized = false;

    @PostConstruct
    void init() {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.info("pureeats.fcm.credentials-path not set — FCM push disabled, notifications will be logged only");
            return;
        }
        try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
            log.info("FCM push enabled (credentials loaded from {})", credentialsPath);
        } catch (IOException e) {
            log.warn("Failed to load Firebase credentials from '{}' — FCM push disabled, notifications will be logged only", credentialsPath, e);
        }
    }

    /** Convenience overload for the common case - a single device token, plain title/body, an optional {@code type} that lands in the data payload (see NotificationType#TEST's caller for why this must never rely on a template). */
    public void send(String token, String title, String body, String type) {
        send(new FcmPushRequest(token, null, title, body, null,
                type != null ? Map.of("type", type) : Map.of(), null, null));
    }

    /**
     * Sends exactly one FCM message per call, to either {@code request.token()} (one device) or
     * {@code request.topic()} (every device subscribed to it) - never both. Builds three parallel
     * views of the same content: a plain {@link Notification} (image only - the generic
     * cross-platform fallback), a top-level data payload (delivered to the app's own code on every
     * platform, display or not), and a {@link WebpushConfig} carrying the full web-specific
     * presentation (image, click-through link, action buttons) - browsers use the richer webpush
     * block when present, so nothing is lost by also setting the plain one.
     */
    public void send(FcmPushRequest request) {
        Map<String, String> data = buildDataPayload(request);
        if (!initialized) {
            log.info("[push-stub] would send to {}={} title='{}' body='{}' data={}",
                    request.token() != null ? "token" : "topic", request.token() != null ? request.token() : request.topic(),
                    request.title(), request.body(), data);
            return;
        }
        if (request.token() == null && request.topic() == null) {
            log.warn("FcmPushRequest for '{}' has neither a token nor a topic — nothing to send to", request.title());
            return;
        }

        Notification.Builder notification = Notification.builder().setTitle(request.title()).setBody(request.body());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            notification.setImage(request.imageUrl());
        }

        Message.Builder message = Message.builder().setNotification(notification.build());
        if (request.token() != null) {
            message.setToken(request.token());
        } else {
            message.setTopic(request.topic());
        }
        if (!data.isEmpty()) {
            message.putAllData(data);
        }
        message.setWebpushConfig(buildWebpushConfig(request));

        try {
            String id = FirebaseMessaging.getInstance().send(message.build());
            log.debug("Sent FCM message {} to {}", id, request.token() != null ? request.token() : "topic:" + request.topic());
        } catch (Exception e) {
            log.warn("Failed to send FCM push to {}", request.token() != null ? request.token() : "topic:" + request.topic(), e);
        }
    }

    /**
     * A custom {@code onBackgroundMessage}/{@code onMessage} handler in the client (both apps'
     * firebaseMessaging.ts / service workers) can't reliably read {@code webpush.notification.actions}
     * or {@code webpush.fcmOptions.link} back off the payload it receives - those are meant for the
     * browser's own default push rendering, which a custom handler bypasses. Mirroring them into the
     * plain data payload (as plain strings - {@code image}/{@code click_action} directly,
     * {@code actions} JSON-encoded since FCM data values must be strings) lets the client reconstruct
     * everything from {@code payload.data} alone and call {@code showNotification} itself with the
     * same image/click target/action buttons. Caller-supplied {@code request.data()} wins on key
     * collision - these are only filled in when absent.
     */
    private Map<String, String> buildDataPayload(FcmPushRequest request) {
        Map<String, String> data = new LinkedHashMap<>(request.data());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            data.putIfAbsent("image", request.imageUrl());
        }
        if (request.clickAction() != null && !request.clickAction().isBlank()) {
            data.putIfAbsent("click_action", request.clickAction());
        }
        if (!request.actions().isEmpty()) {
            try {
                data.putIfAbsent("actions", objectMapper.writeValueAsString(request.actions()));
            } catch (Exception e) {
                log.warn("Failed to serialize FCM actions for '{}', omitting them from the data payload", request.title(), e);
            }
        }
        return data;
    }

    private WebpushConfig buildWebpushConfig(FcmPushRequest request) {
        WebpushNotification.Builder webNotification = WebpushNotification.builder()
                .setTitle(request.title())
                .setBody(request.body());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            webNotification.setImage(request.imageUrl());
        }
        for (FcmAction action : request.actions()) {
            webNotification.addAction(new WebpushNotification.Action(action.action(), action.title(), action.icon()));
        }

        WebpushConfig.Builder webpush = WebpushConfig.builder().setNotification(webNotification.build());
        if (request.clickAction() != null && !request.clickAction().isBlank()) {
            webpush.setFcmOptions(WebpushFcmOptions.withLink(request.clickAction()));
        }
        return webpush.build();
    }

    /** Subscribes every given device token to a topic - once subscribed, {@link #send} with that topic set (and no token) reaches all of them in one call, instead of looping over individual tokens. Safe to call repeatedly; already-subscribed tokens are a no-op. */
    public void subscribeToTopic(List<String> tokens, String topic) {
        if (tokens.isEmpty()) return;
        if (!initialized) {
            log.info("[push-stub] would subscribe {} token(s) to topic '{}'", tokens.size(), topic);
            return;
        }
        try {
            var response = FirebaseMessaging.getInstance().subscribeToTopic(tokens, topic);
            log.info("Subscribed {} token(s) to topic '{}' ({} failure(s))", tokens.size(), topic, response.getFailureCount());
        } catch (Exception e) {
            log.warn("Failed to subscribe {} token(s) to topic '{}'", tokens.size(), topic, e);
        }
    }

    public void unsubscribeFromTopic(List<String> tokens, String topic) {
        if (tokens.isEmpty()) return;
        if (!initialized) {
            log.info("[push-stub] would unsubscribe {} token(s) from topic '{}'", tokens.size(), topic);
            return;
        }
        try {
            var response = FirebaseMessaging.getInstance().unsubscribeFromTopic(tokens, topic);
            log.info("Unsubscribed {} token(s) from topic '{}' ({} failure(s))", tokens.size(), topic, response.getFailureCount());
        } catch (Exception e) {
            log.warn("Failed to unsubscribe {} token(s) from topic '{}'", tokens.size(), topic, e);
        }
    }
}
