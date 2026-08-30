package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.RestaurantAuditLogResponse;
import com.pureeats.catalog.entity.RestaurantAuditLog;
import com.pureeats.catalog.repository.RestaurantAuditLogRepository;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.User;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Field-level "what changed, from what, to what, by whom" trail for restaurant updates. */
@Service
@RequiredArgsConstructor
public class RestaurantAuditLogService {

    private final RestaurantAuditLogRepository restaurantAuditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long restaurantId, String fieldName, Object oldValue, Object newValue, Long updatedBy) {
        RestaurantAuditLog log = new RestaurantAuditLog();
        log.setRestaurantId(restaurantId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue != null ? oldValue.toString() : null);
        log.setNewValue(newValue != null ? newValue.toString() : null);
        log.setUpdatedBy(updatedBy);
        log.setUpdatedAt(LocalDateTime.now());
        restaurantAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantAuditLogResponse> journey(Long restaurantId, Pageable pageable) {
        Page<RestaurantAuditLog> page = restaurantAuditLogRepository.findByRestaurantIdOrderByUpdatedAtDesc(restaurantId, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private RestaurantAuditLogResponse toResponse(RestaurantAuditLog log) {
        String updatedByName = log.getUpdatedBy() != null
                ? userRepository.findById(log.getUpdatedBy()).map(User::getName).orElse(null)
                : null;
        return new RestaurantAuditLogResponse(log.getId(), log.getFieldName(), log.getOldValue(), log.getNewValue(),
                log.getUpdatedBy(), updatedByName, log.getUpdatedAt());
    }
}
