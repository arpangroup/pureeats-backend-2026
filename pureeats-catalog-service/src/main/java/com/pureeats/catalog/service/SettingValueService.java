package com.pureeats.catalog.service;

import com.pureeats.catalog.repository.SettingRepository;
import com.pureeats.domain.entity.Setting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Typed read side of the generic key/value settings store, for business logic that needs to honour
 * a value an admin edits under Settings (see {@link SettingSchemaService}) - e.g. the customer
 * order-rate limit or the rider inactivity timeout. Every getter takes the caller's own fallback,
 * used when the row doesn't exist yet (never saved from the admin panel) or holds something
 * unparseable, so a fresh deployment behaves exactly like the hardcoded/application.yml default it
 * replaces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingValueService {

    private final SettingRepository settingRepository;

    @Transactional(readOnly = true)
    public String getString(String key, String fallback) {
        String value = settingRepository.findByKey(key).map(Setting::getValue).orElse(null);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        String value = getString(key, null);
        if (value == null) return fallback;
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Setting '{}' has non-numeric value '{}' - using fallback {}", key, value, fallback);
            return fallback;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, null);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
