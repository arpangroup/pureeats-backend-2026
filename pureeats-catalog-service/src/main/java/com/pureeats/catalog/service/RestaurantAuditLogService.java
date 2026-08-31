package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.RestaurantAuditLogResponse;
import com.pureeats.catalog.entity.RestaurantAuditLog;
import com.pureeats.catalog.repository.RestaurantAuditLogRepository;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.User;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Field-level "what changed, from what, to what, by whom" trail for restaurant updates. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantAuditLogService {

    private final RestaurantAuditLogRepository restaurantAuditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long restaurantId, String fieldName, Object oldValue, Object newValue, Long updatedBy) {
        log.info("Restaurant {} field '{}' changed from '{}' to '{}' by user {}",
                restaurantId, fieldName, oldValue, newValue, updatedBy);
        RestaurantAuditLog entry = new RestaurantAuditLog();
        entry.setRestaurantId(restaurantId);
        entry.setFieldName(fieldName);
        entry.setOldValue(oldValue != null ? oldValue.toString() : null);
        entry.setNewValue(newValue != null ? newValue.toString() : null);
        entry.setUpdatedBy(updatedBy);
        entry.setUpdatedAt(LocalDateTime.now());
        restaurantAuditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantAuditLogResponse> journey(Long restaurantId, Pageable pageable) {
        log.debug("Fetching audit-log journey for restaurant {}", restaurantId);
        Page<RestaurantAuditLog> page = restaurantAuditLogRepository.findByRestaurantIdOrderByUpdatedAtDesc(restaurantId, pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private RestaurantAuditLogResponse toResponse(RestaurantAuditLog entry) {
        String updatedByName = entry.getUpdatedBy() != null
                ? userRepository.findById(entry.getUpdatedBy()).map(User::getName).orElse(null)
                : null;
        return new RestaurantAuditLogResponse(entry.getId(), entry.getFieldName(), entry.getOldValue(), entry.getNewValue(),
                entry.getUpdatedBy(), updatedByName, entry.getUpdatedAt());
    }
}
