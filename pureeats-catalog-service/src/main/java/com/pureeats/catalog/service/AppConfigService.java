package com.pureeats.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.catalog.dto.AppConfigAdminRequest;
import com.pureeats.catalog.dto.AppConfigAdminResponse;
import com.pureeats.catalog.dto.AppConfigResponse;
import com.pureeats.catalog.dto.DeliveryInstructionOptionDto;
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
                config.googleMapsApiKey(), config.enabledPaymentMethods(), config.forceLogoutOnHardUpdate(),
                config.audioSearchEnabled(), config.promoSliderEnabled(), config.topPicksEnabled(), config.recommendedItemsEnabled(),
                config.restaurantListLayout(), config.recommendedItemsLayout(), config.restaurantItemsLayout(),
                config.deliveryInstructionMode(), config.deliveryInstructionOptions(), config.mapProvider(),
                config.orderStatusUpdateMode(), config.orderStatusPollIntervalMs());
    }

    @Transactional(readOnly = true)
    public AppConfigAdminResponse getForAdmin() {
        AppConfigAdminRequest config = readStored();
        return new AppConfigAdminResponse(config.latestVersion(), config.minSupportedVersion(), config.message(),
                config.googleMapsApiKey(), config.enabledPaymentMethods(), config.forceLogoutOnHardUpdate(),
                config.audioSearchEnabled(), config.promoSliderEnabled(), config.topPicksEnabled(), config.recommendedItemsEnabled(),
                config.restaurantListLayout(), config.recommendedItemsLayout(), config.restaurantItemsLayout(),
                config.deliveryInstructionMode(), config.deliveryInstructionOptions(), config.mapProvider(),
                config.orderStatusUpdateMode(), config.orderStatusPollIntervalMs());
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
                .map(s -> withDefaults(parseJson(s.getValue())))
                .orElseGet(AppConfigService::defaults);
    }

    /** Client-side fallback list on the Cart page's Instructions tab mirrors this exactly - keep the two in sync. */
    private static List<DeliveryInstructionOptionDto> defaultDeliveryInstructionOptions() {
        return List.of(
                new DeliveryInstructionOptionDto("LEAVE_AT_DOOR", "Leave at the door", "DoorOpen"),
                new DeliveryInstructionOptionDto("AVOID_CALLING", "Avoid calling", "PhoneOff"),
                new DeliveryInstructionOptionDto("AVOID_RINGING_BELL", "Avoid ringing bell", "BellOff"),
                new DeliveryInstructionOptionDto("LEAVE_WITH_SECURITY", "Leave with security", "UserCheck"));
    }

    private static AppConfigAdminRequest defaults() {
        return new AppConfigAdminRequest("0.0.0", "0.0.0", null, null, List.of(), false,
                false, true, true, true,
                "TWO_COLUMN", "TWO_COLUMN", "TWO_COLUMN",
                "QUICK_OPTIONS", defaultDeliveryInstructionOptions(), "OSM",
                "POLL", 8000);
    }

    /** Fills any null field (a row stored before this field existed) with its default, so an old/partial stored blob never trips a NPE unboxing a primitive in AppConfigResponse/AppConfigAdminResponse. */
    private static AppConfigAdminRequest withDefaults(AppConfigAdminRequest config) {
        AppConfigAdminRequest d = defaults();
        return new AppConfigAdminRequest(
                config.latestVersion(), config.minSupportedVersion(), config.message(), config.googleMapsApiKey(),
                config.enabledPaymentMethods() != null ? config.enabledPaymentMethods() : d.enabledPaymentMethods(),
                config.forceLogoutOnHardUpdate(),
                config.audioSearchEnabled() != null ? config.audioSearchEnabled() : d.audioSearchEnabled(),
                config.promoSliderEnabled() != null ? config.promoSliderEnabled() : d.promoSliderEnabled(),
                config.topPicksEnabled() != null ? config.topPicksEnabled() : d.topPicksEnabled(),
                config.recommendedItemsEnabled() != null ? config.recommendedItemsEnabled() : d.recommendedItemsEnabled(),
                config.restaurantListLayout() != null ? config.restaurantListLayout() : d.restaurantListLayout(),
                config.recommendedItemsLayout() != null ? config.recommendedItemsLayout() : d.recommendedItemsLayout(),
                config.restaurantItemsLayout() != null ? config.restaurantItemsLayout() : d.restaurantItemsLayout(),
                config.deliveryInstructionMode() != null ? config.deliveryInstructionMode() : d.deliveryInstructionMode(),
                config.deliveryInstructionOptions() != null ? config.deliveryInstructionOptions() : d.deliveryInstructionOptions(),
                config.mapProvider() != null ? config.mapProvider() : d.mapProvider(),
                config.orderStatusUpdateMode() != null ? config.orderStatusUpdateMode() : d.orderStatusUpdateMode(),
                config.orderStatusPollIntervalMs() != null ? config.orderStatusPollIntervalMs() : d.orderStatusPollIntervalMs());
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
