package com.pureeats.user.service;

import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.entity.Setting;
import com.pureeats.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which profile fields a delivery partner may change themselves - Settings -> Delivery Application ->
 * Profile editing (all off by default). The keys mirror catalog-service's SettingSchemaService.DRIVER_EDIT_*
 * constants; they're repeated here because user-service can't depend on catalog-service (module cycle),
 * and read through the notification module's settings repository user-service already depends on.
 */
@Component
@RequiredArgsConstructor
public class RiderProfileEditPolicy {

    public static final String NAME = "driver_edit_name";
    public static final String VEHICLE_NUMBER = "driver_edit_vehicle_number";
    public static final String AGE = "driver_edit_age";
    public static final String GENDER = "driver_edit_gender";
    public static final String ABOUT = "driver_edit_about";
    public static final String PHONE = "driver_edit_phone";
    public static final String EMAIL = "driver_edit_email";
    public static final String LICENSE = "driver_edit_license";
    public static final String ID_PROOF = "driver_edit_id_proof";
    public static final String VEHICLE_TYPE = "driver_edit_vehicle_type";
    public static final String PAYOUT = "driver_edit_payout";

    /** Throws unless the field group may be changed from the app (used when a change was detected). */
    public void assertEditable(String key, String label) {
        if (!isEditable(key)) {
            throw new BadRequestException(label + " can't be changed from the app - please contact support to update it.");
        }
    }

    private final NotificationSettingRepository settingRepository;

    public boolean isEditable(String key) {
        return settingRepository.findByKey(key).map(Setting::getValue).map(v -> Boolean.parseBoolean(v.trim())).orElse(false);
    }

    /** Throws when the partner tries to change a locked field. Unchanged values are fine (the app resends the whole form). */
    public void assertCanChange(String key, String label, Object currentValue, Object requestedValue) {
        if (requestedValue == null) return;
        String requested = requestedValue.toString().trim();
        String current = currentValue == null ? "" : currentValue.toString().trim();
        if (requested.equalsIgnoreCase(current)) return;
        if (!isEditable(key)) {
            throw new BadRequestException(label + " can't be changed from the app - please contact support to update it.");
        }
    }
}
