package com.pureeats.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Sends real FCM push via the Firebase Admin SDK when {@code pureeats.fcm.credentials-path} points
 * at a valid service-account JSON; otherwise (no property set - the default, since no Firebase
 * project exists yet - or the file is missing/invalid) every {@link #send} call just logs what it
 * would have sent, same as the stub this replaced. Never throws back to the caller
 * ({@link PushNotificationSender}) - a push failure must never block persisting the in-app
 * {@code Alert} or the caller's own transaction (order acceptance, delivery assignment, ...).
 */
@Slf4j
@Component
public class FcmSender {

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

    public void send(String token, String title, String body, String type) {
        if (!initialized) {
            log.info("[push-stub] would send to token={} title='{}' body='{}' type={}", token, title, body, type);
            return;
        }
        Message.Builder message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build());
        if (type != null) message.putData("type", type);
        try {
            String id = FirebaseMessaging.getInstance().send(message.build());
            log.debug("Sent FCM message {} to token={}", id, token);
        } catch (Exception e) {
            log.warn("Failed to send FCM push to token={}", token, e);
        }
    }
}
