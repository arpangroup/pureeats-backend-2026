package com.pureeats.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.Alert;
import com.pureeats.notification.dto.AlertResponse;
import com.pureeats.notification.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final int RECENT_DAYS = 7;
    private static final int MAX_RESULTS = 20;

    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AlertResponse> listRecent(Long userId) {
        return alertRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, LocalDateTime.now().minus(RECENT_DAYS, ChronoUnit.DAYS))
                .stream().limit(MAX_RESULTS)
                .map(a -> new AlertResponse(a.getId(), parseData(a.getData()), Boolean.TRUE.equals(a.getIsRead()), a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<Alert> unread = alertRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(a -> a.setIsRead(true));
        alertRepository.saveAll(unread);
    }

    @Transactional
    public void markRead(Long userId, Long alertId) {
        Alert alert = findOwnedOrThrow(userId, alertId);
        alert.setIsRead(true);
        alertRepository.save(alert);
    }

    @Transactional
    public void delete(Long userId, Long alertId) {
        alertRepository.delete(findOwnedOrThrow(userId, alertId));
    }

    private Alert findOwnedOrThrow(Long userId, Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + alertId));
        if (!alert.getUserId().equals(userId)) {
            throw new com.pureeats.domain.common.exception.ForbiddenException("This notification does not belong to you");
        }
        return alert;
    }

    private Map<String, Object> parseData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("title", json, "body", "");
        }
    }
}
