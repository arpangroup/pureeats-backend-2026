package com.pureeats.notification.service;

import com.pureeats.domain.entity.Alert;
import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.repository.AlertRepository;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The one bean every other module (order-service, rating-service, ...) calls to notify a user.
 * No FCM/APNs credentials are wired up yet, so the "push" itself is logged rather than sent -
 * the in-app {@code Alert} record is always persisted so {@code GET /api/v1/notifications} works
 * end-to-end regardless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final AlertRepository alertRepository;
    private final PushTokenRepository pushTokenRepository;

    @Transactional
    public void notifyUser(Long userId, String title, String body) {
        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setData("{\"title\":\"" + escape(title) + "\",\"body\":\"" + escape(body) + "\"}");
        alert.setIsRead(false);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alertRepository.save(alert);

        for (PushToken token : pushTokenRepository.findByUserIdAndIsActiveTrue(userId.intValue())) {
            log.info("[push-stub] would send to token={} title='{}' body='{}'", token.getToken(), title, body);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
