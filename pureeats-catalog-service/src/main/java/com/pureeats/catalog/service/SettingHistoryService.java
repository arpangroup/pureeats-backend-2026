package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.SettingFieldDefinition;
import com.pureeats.catalog.dto.SettingHistoryResponse;
import com.pureeats.catalog.entity.SettingHistory;
import com.pureeats.catalog.repository.SettingHistoryRepository;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.User;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Field-level "what changed, from what, to what, by whom" trail for the two settings stores
 * (ContentService#updateSettings, AppConfigService#update) — same shape/convention as
 * RestaurantAuditLogService, just not scoped to one entity id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingHistoryService {

    public static final String SOURCE_SETTING = "SETTING";
    public static final String SOURCE_APP_CONFIG = "APP_CONFIG";

    /** AppConfig fields that are never stored in the clear here, on top of whatever the generic settings schema itself already marks as "password" (see isSensitive). */
    private static final Set<String> ALWAYS_REDACTED_FIELDS = Set.of("razorpayKeySecret");
    private static final String REDACTED_PLACEHOLDER = "••••••••";

    private final SettingHistoryRepository settingHistoryRepository;
    private final SettingSchemaService settingSchemaService;
    private final UserRepository userRepository;

    /** Records one field's change — a no-op if oldValue and newValue are actually equal (nothing to log), which is what makes it safe to call unconditionally rather than requiring the caller to pre-filter. */
    @Transactional
    public void record(String source, String fieldKey, String oldValue, String newValue, Long updatedBy) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        boolean redact = isSensitive(fieldKey);
        log.info("Setting [{}] '{}' changed by user {}{}", source, fieldKey, updatedBy, redact ? " (value redacted)" : "");
        SettingHistory entry = new SettingHistory();
        entry.setSource(source);
        entry.setFieldKey(fieldKey);
        entry.setOldValue(redact ? redactedOrNull(oldValue) : oldValue);
        entry.setNewValue(redact ? redactedOrNull(newValue) : newValue);
        entry.setUpdatedBy(updatedBy);
        entry.setUpdatedAt(LocalDateTime.now());
        settingHistoryRepository.save(entry);
    }

    /**
     * Diffs two records of the same type component-by-component (reflection over
     * {@link Class#getRecordComponents()}) and records one history row per field that actually
     * changed — built for AppConfigAdminRequest specifically (before/after the merge in
     * AppConfigService#update), but works for any record pair. New fields added to the record are
     * picked up automatically, same as the rest of this settings system.
     */
    @Transactional
    public void recordDiffs(String source, Record before, Record after, Long updatedBy) {
        if (before.getClass() != after.getClass()) {
            log.warn("Cannot diff records of different types: {} vs {}", before.getClass(), after.getClass());
            return;
        }
        for (RecordComponent component : before.getClass().getRecordComponents()) {
            try {
                Object oldValue = component.getAccessor().invoke(before);
                Object newValue = component.getAccessor().invoke(after);
                if (!Objects.equals(oldValue, newValue)) {
                    record(source, component.getName(), stringify(oldValue), stringify(newValue), updatedBy);
                }
            } catch (ReflectiveOperationException e) {
                log.warn("Could not diff field '{}': {}", component.getName(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<SettingHistoryResponse> list(String fieldKey, String source, Pageable pageable) {
        Page<SettingHistory> page = fieldKey != null && !fieldKey.isBlank()
                ? settingHistoryRepository.findByFieldKeyOrderByUpdatedAtDesc(fieldKey, pageable)
                : source != null && !source.isBlank()
                ? settingHistoryRepository.findBySourceOrderByUpdatedAtDesc(source, pageable)
                : settingHistoryRepository.findAllByOrderByUpdatedAtDesc(pageable);
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private SettingHistoryResponse toResponse(SettingHistory entry) {
        String updatedByName = entry.getUpdatedBy() != null
                ? userRepository.findById(entry.getUpdatedBy()).map(User::getName).orElse(null)
                : null;
        return new SettingHistoryResponse(entry.getId(), entry.getSource(), entry.getFieldKey(),
                entry.getOldValue(), entry.getNewValue(), entry.getUpdatedBy(), updatedByName, entry.getUpdatedAt());
    }

    private boolean isSensitive(String fieldKey) {
        if (ALWAYS_REDACTED_FIELDS.contains(fieldKey)) {
            return true;
        }
        return settingSchemaService.schema().stream()
                .flatMap(s -> s.groups().stream())
                .flatMap(g -> g.fields().stream())
                .filter(f -> f.key().equals(fieldKey))
                .map(SettingFieldDefinition::fieldType)
                .anyMatch("password"::equals);
    }

    private String redactedOrNull(String value) {
        return (value == null || value.isBlank()) ? null : REDACTED_PLACEHOLDER;
    }

    private String stringify(Object value) {
        return value == null ? null : value.toString();
    }
}
