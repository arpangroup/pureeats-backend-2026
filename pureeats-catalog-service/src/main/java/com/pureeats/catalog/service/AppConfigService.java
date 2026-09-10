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
import org.springframework.beans.factory.annotation.Value;
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
    private final SettingHistoryService settingHistoryService;

    /** Blank means the settings-confirmation-password feature is off. Deploy-time only — no admin-panel UI sets this, only this env-backed property (see application.yml). */
    @Value("${pureeats.settings.confirmation-password:}")
    private String settingsConfirmationPassword;

    /** Computes update severity for the given client version against the stored config - never trusts the client's own opinion of whether it's current. */
    @Transactional(readOnly = true)
    public AppConfigResponse forClient(String clientVersion) {
        AppConfigAdminRequest config = readStored();
        String severity = severityFor(clientVersion, config);
        return new AppConfigResponse(severity, config.message(), config.latestVersion(),
                config.googleMapsApiKey(), config.enabledPaymentMethods(), Boolean.TRUE.equals(config.forceLogoutOnHardUpdate()),
                config.audioSearchEnabled(), config.promoSliderEnabled(), config.topPicksEnabled(), config.recommendedItemsEnabled(),
                config.restaurantListLayout(), config.recommendedItemsLayout(), config.restaurantItemsLayout(),
                config.deliveryInstructionMode(), config.deliveryInstructionOptions(), config.mapProvider(),
                config.orderStatusUpdateMode(), config.orderStatusPollIntervalMs(),
                config.locationResolutionAuthenticatedPriority(), config.locationResolutionGuestPriority(),
                config.locationResolutionAuthenticatedFallbackLabel(), config.locationResolutionGuestFallbackLabel(),
                config.razorpayKeyId(), config.firebaseApiKey(), config.firebaseAuthDomain(), config.firebaseProjectId(),
                config.firebaseStorageBucket(), config.firebaseMessagingSenderId(), config.firebaseAppId(), config.firebaseVapidKey());
    }

    @Transactional(readOnly = true)
    public AppConfigAdminResponse getForAdmin() {
        AppConfigAdminRequest config = readStored();
        return new AppConfigAdminResponse(config.latestVersion(), config.minSupportedVersion(), config.message(),
                config.googleMapsApiKey(), config.enabledPaymentMethods(), Boolean.TRUE.equals(config.forceLogoutOnHardUpdate()),
                config.audioSearchEnabled(), config.promoSliderEnabled(), config.topPicksEnabled(), config.recommendedItemsEnabled(),
                config.restaurantListLayout(), config.recommendedItemsLayout(), config.restaurantItemsLayout(),
                config.deliveryInstructionMode(), config.deliveryInstructionOptions(), config.mapProvider(),
                config.orderStatusUpdateMode(), config.orderStatusPollIntervalMs(),
                config.locationResolutionAuthenticatedPriority(), config.locationResolutionGuestPriority(),
                config.locationResolutionAuthenticatedFallbackLabel(), config.locationResolutionGuestFallbackLabel(),
                config.razorpayKeyId(), config.razorpayKeySecret() != null && !config.razorpayKeySecret().isBlank(),
                config.firebaseApiKey(), config.firebaseAuthDomain(), config.firebaseProjectId(),
                config.firebaseStorageBucket(), config.firebaseMessagingSenderId(), config.firebaseAppId(), config.firebaseVapidKey(),
                settingsConfirmationPassword != null && !settingsConfirmationPassword.isBlank());
    }

    /** The one value never exposed through either response above — read directly by RazorpayService when it needs to actually call Razorpay's API. */
    @Transactional(readOnly = true)
    public String getRazorpaySecret() {
        return readStored().razorpayKeySecret();
    }

    @Transactional(readOnly = true)
    public String getRazorpayKeyId() {
        return readStored().razorpayKeyId();
    }

    /**
     * Every field in {@code request} that's null is left exactly as it already was (see
     * {@link #mergeOntoExisting}) — so a caller only ever needs to send the field(s) that actually
     * changed, not a full round-trip of the current config. {@code updatedBy} is the acting admin's
     * user id; one {@link SettingHistory} row is recorded per field whose merged value actually
     * differs from what was stored before, via a generic reflective diff
     * ({@link SettingHistoryService#recordDiffs}) rather than 30 hand-written comparisons — a field
     * added to {@link AppConfigAdminRequest} gets history tracking for free.
     */
    @Transactional
    public AppConfigAdminResponse update(AppConfigAdminRequest request, Long updatedBy) {
        log.info("Admin {} updating app config, {} non-null field(s) in request", updatedBy, nonNullFieldCount(request));
        AppConfigAdminRequest before = readStored();
        AppConfigAdminRequest merged = mergeOntoExisting(request, before);
        Setting setting = settingRepository.findByKey(SETTING_KEY).orElseGet(() -> {
            Setting created = new Setting();
            created.setKey(SETTING_KEY);
            return created;
        });
        setting.setValue(writeJson(merged));
        settingRepository.save(setting);
        settingHistoryService.recordDiffs(SettingHistoryService.SOURCE_APP_CONFIG, before, merged, updatedBy);
        return getForAdmin();
    }

    private long nonNullFieldCount(AppConfigAdminRequest request) {
        return java.util.Arrays.stream(request.getClass().getRecordComponents())
                .filter(c -> {
                    try {
                        return c.getAccessor().invoke(request) != null;
                    } catch (ReflectiveOperationException e) {
                        return false;
                    }
                }).count();
    }

    /**
     * update() used to replace the entire stored blob wholesale - fine as long as every caller
     * always round-tripped the full current config, which is exactly the assumption this method
     * removes: any field left null in {@code request} keeps whatever's in {@code existing} instead
     * of being wiped out. This generalizes what used to be one-field-only special-casing for
     * razorpayKeySecret (never echoed back to the browser, so a blank submission had to mean "leave
     * it alone") - now every field gets that same "null means unchanged" treatment.
     */
    private AppConfigAdminRequest mergeOntoExisting(AppConfigAdminRequest request, AppConfigAdminRequest existing) {
        return new AppConfigAdminRequest(
                request.latestVersion() != null ? request.latestVersion() : existing.latestVersion(),
                request.minSupportedVersion() != null ? request.minSupportedVersion() : existing.minSupportedVersion(),
                request.message() != null ? request.message() : existing.message(),
                request.googleMapsApiKey() != null ? request.googleMapsApiKey() : existing.googleMapsApiKey(),
                request.enabledPaymentMethods() != null ? request.enabledPaymentMethods() : existing.enabledPaymentMethods(),
                request.forceLogoutOnHardUpdate() != null ? request.forceLogoutOnHardUpdate() : existing.forceLogoutOnHardUpdate(),
                request.audioSearchEnabled() != null ? request.audioSearchEnabled() : existing.audioSearchEnabled(),
                request.promoSliderEnabled() != null ? request.promoSliderEnabled() : existing.promoSliderEnabled(),
                request.topPicksEnabled() != null ? request.topPicksEnabled() : existing.topPicksEnabled(),
                request.recommendedItemsEnabled() != null ? request.recommendedItemsEnabled() : existing.recommendedItemsEnabled(),
                request.restaurantListLayout() != null ? request.restaurantListLayout() : existing.restaurantListLayout(),
                request.recommendedItemsLayout() != null ? request.recommendedItemsLayout() : existing.recommendedItemsLayout(),
                request.restaurantItemsLayout() != null ? request.restaurantItemsLayout() : existing.restaurantItemsLayout(),
                request.deliveryInstructionMode() != null ? request.deliveryInstructionMode() : existing.deliveryInstructionMode(),
                request.deliveryInstructionOptions() != null ? request.deliveryInstructionOptions() : existing.deliveryInstructionOptions(),
                request.mapProvider() != null ? request.mapProvider() : existing.mapProvider(),
                request.orderStatusUpdateMode() != null ? request.orderStatusUpdateMode() : existing.orderStatusUpdateMode(),
                request.orderStatusPollIntervalMs() != null ? request.orderStatusPollIntervalMs() : existing.orderStatusPollIntervalMs(),
                request.locationResolutionAuthenticatedPriority() != null ? request.locationResolutionAuthenticatedPriority() : existing.locationResolutionAuthenticatedPriority(),
                request.locationResolutionGuestPriority() != null ? request.locationResolutionGuestPriority() : existing.locationResolutionGuestPriority(),
                request.locationResolutionAuthenticatedFallbackLabel() != null ? request.locationResolutionAuthenticatedFallbackLabel() : existing.locationResolutionAuthenticatedFallbackLabel(),
                request.locationResolutionGuestFallbackLabel() != null ? request.locationResolutionGuestFallbackLabel() : existing.locationResolutionGuestFallbackLabel(),
                request.razorpayKeyId() != null ? request.razorpayKeyId() : existing.razorpayKeyId(),
                // The one field where "not sent" and "sent blank" both mean the same thing (leave
                // it alone) - the admin form never has the current secret to send back in the first
                // place (getForAdmin only ever exposes razorpayKeySecretSet), so a blank string here
                // is exactly as much "no change intended" as a genuinely absent field.
                request.razorpayKeySecret() != null && !request.razorpayKeySecret().isBlank() ? request.razorpayKeySecret() : existing.razorpayKeySecret(),
                request.firebaseApiKey() != null ? request.firebaseApiKey() : existing.firebaseApiKey(),
                request.firebaseAuthDomain() != null ? request.firebaseAuthDomain() : existing.firebaseAuthDomain(),
                request.firebaseProjectId() != null ? request.firebaseProjectId() : existing.firebaseProjectId(),
                request.firebaseStorageBucket() != null ? request.firebaseStorageBucket() : existing.firebaseStorageBucket(),
                request.firebaseMessagingSenderId() != null ? request.firebaseMessagingSenderId() : existing.firebaseMessagingSenderId(),
                request.firebaseAppId() != null ? request.firebaseAppId() : existing.firebaseAppId(),
                request.firebaseVapidKey() != null ? request.firebaseVapidKey() : existing.firebaseVapidKey());
    }

    /**
     * Gate used by both AppConfigController#update and AdminSettingsController#update before any
     * write is applied. Fails open (true) when pureeats.settings.confirmation-password isn't set -
     * the feature is off and a raw password, if one was even sent, is accepted and ignored since
     * nothing requires it. Otherwise it's a plain equality check against that env-configured value -
     * there's no stored/hashed copy to compare against since this is never admin-editable, only
     * deploy-time config (see the settingsConfirmationPassword field above).
     */
    public boolean verifyConfirmationPassword(String rawPassword) {
        if (settingsConfirmationPassword == null || settingsConfirmationPassword.isBlank()) {
            return true;
        }
        return settingsConfirmationPassword.equals(rawPassword);
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

    /** Mirrors defaultLocationResolutionConfig in the customer app's src/config/locationResolution.ts — keep the two in sync. */
    private static AppConfigAdminRequest defaults() {
        return new AppConfigAdminRequest("0.0.0", "0.0.0", null, null, List.of(), false,
                false, true, true, true,
                "TWO_COLUMN", "TWO_COLUMN", "TWO_COLUMN",
                "QUICK_OPTIONS", defaultDeliveryInstructionOptions(), "OSM",
                "POLL", 8000,
                List.of("saved", "gps", "ip"), List.of("gps", "ip"),
                "Set your location", "Other",
                null, null, null, null, null, null, null, null, null);
    }

    /** Fills any null field (a row stored before this field existed) with its default, so an old/partial stored blob never trips a NPE unboxing a primitive in AppConfigResponse/AppConfigAdminResponse. */
    private static AppConfigAdminRequest withDefaults(AppConfigAdminRequest config) {
        AppConfigAdminRequest d = defaults();
        return new AppConfigAdminRequest(
                config.latestVersion() != null ? config.latestVersion() : d.latestVersion(),
                config.minSupportedVersion() != null ? config.minSupportedVersion() : d.minSupportedVersion(),
                config.message(), config.googleMapsApiKey(),
                config.enabledPaymentMethods() != null ? config.enabledPaymentMethods() : d.enabledPaymentMethods(),
                config.forceLogoutOnHardUpdate() != null ? config.forceLogoutOnHardUpdate() : d.forceLogoutOnHardUpdate(),
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
                config.orderStatusPollIntervalMs() != null ? config.orderStatusPollIntervalMs() : d.orderStatusPollIntervalMs(),
                config.locationResolutionAuthenticatedPriority() != null ? config.locationResolutionAuthenticatedPriority() : d.locationResolutionAuthenticatedPriority(),
                config.locationResolutionGuestPriority() != null ? config.locationResolutionGuestPriority() : d.locationResolutionGuestPriority(),
                config.locationResolutionAuthenticatedFallbackLabel() != null ? config.locationResolutionAuthenticatedFallbackLabel() : d.locationResolutionAuthenticatedFallbackLabel(),
                config.locationResolutionGuestFallbackLabel() != null ? config.locationResolutionGuestFallbackLabel() : d.locationResolutionGuestFallbackLabel(),
                config.razorpayKeyId(), config.razorpayKeySecret(),
                config.firebaseApiKey(), config.firebaseAuthDomain(), config.firebaseProjectId(),
                config.firebaseStorageBucket(), config.firebaseMessagingSenderId(), config.firebaseAppId(), config.firebaseVapidKey());
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
