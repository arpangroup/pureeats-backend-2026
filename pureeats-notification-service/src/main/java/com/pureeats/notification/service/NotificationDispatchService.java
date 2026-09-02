package com.pureeats.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.domain.entity.Alert;
import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.repository.AlertRepository;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one bean every other module (order-service, rating-service, ...) calls to notify a user.
 * The in-app {@code Alert} record is always persisted so {@code GET /api/v1/notifications} works
 * regardless of push delivery. Real FCM sending is behind {@link FcmSender} - with no service
 * account configured (no Firebase project exists yet - see application.yml
 * `pureeats.fcm.credentials-path`), it stays a log-only stub exactly as before.
 * <p>
 * {@code type} is a free-form category the client groups/badges by (e.g. "ORDER_UPDATE",
 * "PROMOTION", "OFFER") - pass whatever is meaningful for the call site; {@code null} is fine for a
 * generic alert. See NotificationsPage.tsx's TYPE_STYLE map on the client for the currently known set.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final AlertRepository alertRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ObjectMapper objectMapper;
    private final FcmSender fcmSender;

    /** Convenience overload for the common case of no explicit category. */
    @Transactional
    public void notifyUser(Long userId, String title, String body) {
        notifyUser(userId, title, body, null);
    }

    @Transactional
    public void notifyUser(Long userId, String title, String body, String type) {
        log.info("Notifying user {}: {} (type={})", userId, title, type);
        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setData(writeData(title, body, type));
        alert.setIsRead(false);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alertRepository.save(alert);

        List<PushToken> tokens = pushTokenRepository.findByUserIdAndIsActiveTrue(userId.intValue());
        log.debug("Found {} active push token(s) for user {}", tokens.size(), userId);
        for (PushToken token : tokens) {
            fcmSender.send(token.getToken(), title, body, type);
        }
    }

    private String writeData(String title, String body, String type) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title);
        data.put("body", body);
        if (type != null) data.put("type", type);
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize notification data, falling back to a minimal payload", e);
            return "{\"title\":\"" + title.replace("\"", "\\\"") + "\"}";
        }
    }
}
