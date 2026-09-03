package com.pureeats.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.domain.entity.Alert;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists an in-app {@code Alert} row so {@code GET /api/v1/notifications} (the notification bell)
 * shows it regardless of whether any push/email/SMS channel was also selected for this request -
 * this used to be inlined directly into every order/rating call site before every channel became
 * independently pluggable; kept as its own channel so it participates in the same config-driven
 * fan-out as PUSH/EMAIL/SMS/WHATSAPP instead of being hardcoded into every caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationSender implements ChannelNotificationSender {

    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    @Transactional
    public NotificationResult send(NotificationRequest request) {
        if (request.userId() == null) {
            log.warn("Cannot record IN_APP alert for type {} - no userId on the request", request.type());
            return NotificationResult.failure("No userId to attach the alert to");
        }
        String title = String.valueOf(request.params().getOrDefault("title", request.type().name()));
        String body = String.valueOf(request.params().getOrDefault("body", ""));
        String category = request.params().get("category") != null ? String.valueOf(request.params().get("category")) : null;

        Alert alert = new Alert();
        alert.setUserId(request.userId());
        alert.setData(writeData(title, body, category));
        alert.setIsRead(false);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        alertRepository.save(alert);
        log.debug("Recorded IN_APP alert {} for user {}", alert.getId(), request.userId());
        return NotificationResult.success(String.valueOf(alert.getId()));
    }

    private String writeData(String title, String body, String category) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title);
        data.put("body", body);
        if (category != null) data.put("type", category);
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize alert data, falling back to a minimal payload", e);
            return "{\"title\":\"" + title.replace("\"", "\\\"") + "\"}";
        }
    }
}
