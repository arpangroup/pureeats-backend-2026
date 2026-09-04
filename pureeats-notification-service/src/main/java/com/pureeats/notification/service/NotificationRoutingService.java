package com.pureeats.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pureeats.domain.entity.Setting;
import com.pureeats.notification.dto.NotificationRoutingConfig;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationRecipientRole;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single admin-configurable answer to "which channel(s) fire for which notification, to
 * whom" - stored as one JSON blob in the generic {@link Setting} key/value table (key
 * {@value #SETTING_KEY}), same shape as catalog-service's {@code AppConfigService}. Two independent
 * questions:
 * <ul>
 *   <li>{@link #extraChannelsFor(NotificationType)} - for OTP-style flows where the caller already
 *       picked one required channel (SMS to a phone, EMAIL to an address), which ADDITIONAL
 *       channels should also fire (console trace, a push heads-up, WhatsApp, ...)? Defaults to none,
 *       so existing OTP delivery behavior is unchanged until an admin opts in.</li>
 *   <li>{@link #orderStatusChannelsFor(NotificationRecipientRole)} - for an order-status change,
 *       which channel(s) should the given recipient role receive it on? Defaults to IN_APP (+PUSH for
 *       customer/delivery-partner) so push actually reaches devices out of the box, while staying
 *       fully admin-editable without a code change.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRoutingService {

    private static final String SETTING_KEY = "notification_routing";

    private final NotificationSettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Set<NotificationChannel> extraChannelsFor(NotificationType type) {
        return parseChannels(readStored().extraChannelsByNotificationType(), type.name());
    }

    @Transactional(readOnly = true)
    public Set<NotificationChannel> orderStatusChannelsFor(NotificationRecipientRole role) {
        return parseChannels(readStored().orderStatusChannelsByRole(), role.name());
    }

    @Transactional(readOnly = true)
    public NotificationRoutingConfig getConfig() {
        return readStored();
    }

    @Transactional
    public NotificationRoutingConfig update(NotificationRoutingConfig request) {
        log.info("Admin updating notification routing config");
        Setting setting = settingRepository.findByKey(SETTING_KEY).orElseGet(() -> {
            Setting created = new Setting();
            created.setKey(SETTING_KEY);
            return created;
        });
        setting.setValue(writeJson(request));
        settingRepository.save(setting);
        return readStored();
    }

    private Set<NotificationChannel> parseChannels(Map<String, List<String>> byKey, String key) {
        if (byKey == null) {
            return EnumSet.noneOf(NotificationChannel.class);
        }
        List<String> raw = byKey.get(key);
        if (raw == null || raw.isEmpty()) {
            return EnumSet.noneOf(NotificationChannel.class);
        }
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        for (String value : raw) {
            try {
                channels.add(NotificationChannel.valueOf(value.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring unknown notification channel '{}' configured for '{}'", value, key);
            }
        }
        return channels;
    }

    private NotificationRoutingConfig readStored() {
        return settingRepository.findByKey(SETTING_KEY)
                .map(s -> parseJson(s.getValue()))
                .orElseGet(NotificationRoutingService::defaults);
    }

    private static NotificationRoutingConfig defaults() {
        return new NotificationRoutingConfig(
                Map.of(),
                Map.of(
                        "CUSTOMER", List.of("IN_APP", "PUSH"),
                        "STORE_OWNER", List.of("IN_APP"),
                        "DELIVERY_PARTNER", List.of("IN_APP", "PUSH"),
                        "ADMIN", List.of("IN_APP")
                ));
    }

    private NotificationRoutingConfig parseJson(String json) {
        if (json == null || json.isBlank()) return defaults();
        try {
            return objectMapper.readValue(json, NotificationRoutingConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse stored notification routing config, falling back to defaults", e);
            return defaults();
        }
    }

    private String writeJson(NotificationRoutingConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize notification routing config", e);
            throw new IllegalStateException("Could not save notification routing config", e);
        }
    }
}
