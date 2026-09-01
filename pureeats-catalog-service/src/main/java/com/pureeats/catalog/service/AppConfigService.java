package com.pureeats.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.dto.AppConfigAdminRequest;
import com.pureeats.catalog.dto.AppConfigAdminResponse;
import com.pureeats.catalog.dto.AppConfigResponse;
import com.pureeats.catalog.repository.SettingRepository;
import com.pureeats.domain.entity.Setting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backs the "app update" feature: admins publish a target/minimum version + feature payload
 * (Google Maps key, enabled payment methods) here; the app compares its own version against it on
 * boot to decide whether to nudge (SOFT) or block (HARD) until updated. Stored as one JSON blob in
 * the generic {@link Setting} key/value table (key {@value #SETTING_KEY}) rather than a dedicated
 * table, since it's a single admin-edited row with no query/filter needs of its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppConfigService {

    private static final String SETTING_KEY = "app_config";

    private final SettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    /** Computes update severity for the given client version against the stored config - never trusts the client's own opinion of whether it's current. */
    @Transactional(readOnly = true)
    public AppConfigResponse forClient(String clientVersion) {
        AppConfigAdminRequest config = readStored();
        String severity = severityFor(clientVersion, config);
        return new AppConfigResponse(severity, config.message(), config.latestVersion(),
                config.googleMapsApiKey(), config.enabledPaymentMethods(), config.forceLogoutOnHardUpdate());
    }

    @Transactional(readOnly = true)
    public AppConfigAdminResponse getForAdmin() {
        AppConfigAdminRequest config = readStored();
        return new AppConfigAdminResponse(config.latestVersion(), config.minSupportedVersion(), config.message(),
                config.googleMapsApiKey(), config.enabledPaymentMethods(), config.forceLogoutOnHardUpdate());
    }

    @Transactional
    public AppConfigAdminResponse update(AppConfigAdminRequest request) {
        log.info("Admin updating app config: latestVersion={} minSupportedVersion={} forceLogoutOnHardUpdate={}",
                request.latestVersion(), request.minSupportedVersion(), request.forceLogoutOnHardUpdate());
        Setting setting = settingRepository.findByKey(SETTING_KEY).orElseGet(() -> {
            Setting created = new Setting();
            created.setKey(SETTING_KEY);
            return created;
        });
        setting.setValue(writeJson(request));
        settingRepository.save(setting);
        return getForAdmin();
    }

    private String severityFor(String clientVersion, AppConfigAdminRequest config) {
        if (clientVersion == null || clientVersion.isBlank()) {
            return "NONE";
        }
        if (config.minSupportedVersion() != null && SemverComparator.compare(clientVersion, config.minSupportedVersion()) < 0) {
            return "HARD";
        }
        if (config.latestVersion() != null && SemverComparator.compare(clientVersion, config.latestVersion()) < 0) {
            return "SOFT";
        }
        return "NONE";
    }

    private AppConfigAdminRequest readStored() {
        return settingRepository.findByKey(SETTING_KEY)
                .map(s -> parseJson(s.getValue()))
                .orElseGet(AppConfigService::defaults);
    }

    private static AppConfigAdminRequest defaults() {
        return new AppConfigAdminRequest("0.0.0", "0.0.0", null, null, List.of(), false);
    }

    private AppConfigAdminRequest parseJson(String json) {
        if (json == null || json.isBlank()) return defaults();
        try {
            return objectMapper.readValue(json, AppConfigAdminRequest.class);
        } catch (Exception e) {
            log.warn("Failed to parse stored app config, falling back to defaults", e);
            return defaults();
        }
    }

    private String writeJson(AppConfigAdminRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to serialize app config", e);
            throw new IllegalStateException("Could not save app config", e);
        }
    }
}
