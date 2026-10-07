package com.pureeats.user.security;

import com.pureeats.domain.entity.Setting;
import com.pureeats.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Admin-configurable messages for accounts that can't be used - Settings -> General -> Account messages.
 * Keys mirror catalog-service's SettingSchemaService.ACCOUNT_*_MESSAGE (user-service can't depend on catalog).
 */
@Component
@RequiredArgsConstructor
public class AccountMessages {

    public static final String BLOCKED_KEY = "account_blocked_message";
    public static final String DELETED_KEY = "account_deleted_message";
    public static final String DEFAULT_BLOCKED = "User has been blocked. Please contact customer support.";
    public static final String DEFAULT_DELETED = "This account has been deleted. Please contact customer support.";

    private final NotificationSettingRepository settingRepository;

    public String blocked() {
        return read(BLOCKED_KEY, DEFAULT_BLOCKED);
    }

    public String deleted() {
        return read(DELETED_KEY, DEFAULT_DELETED);
    }

    private String read(String key, String fallback) {
        return settingRepository.findByKey(key).map(Setting::getValue).filter(v -> !v.isBlank()).map(String::trim).orElse(fallback);
    }
}
