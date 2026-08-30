package com.pureeats.user.security.audit;

import com.pureeats.user.entity.AuditLog;
import com.pureeats.user.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogSecurityEventPublisher implements SecurityEventPublisher {

    private final AuditLogRepository auditLogRepository;

    /**
     * Runs in its own transaction so an audit-write failure (or the caller's transaction later
     * rolling back for an unrelated reason) never erases the fact that a security event happened.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(SecurityEvent event) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEventType(event.eventType());
            entry.setUserId(event.userId());
            entry.setRequestId(event.requestId());
            entry.setIpAddress(event.ipAddress());
            entry.setUserAgent(event.userAgent());
            entry.setDeviceId(event.deviceId());
            entry.setEndpoint(event.endpoint());
            entry.setHttpMethod(event.httpMethod());
            entry.setResult(event.result());
            entry.setFailureReason(event.failureReason());
            entry.setMetadata(toSimpleString(event.metadata()));
            entry.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist audit event {}: {}", event.eventType(), e.getMessage());
        }
    }

    private String toSimpleString(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return metadata.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }
}
