package com.pureeats.catalog.dto;

import java.util.Map;

/**
 * PUT /api/v1/admin/settings' actual body — wraps the key/value updates alongside the optional
 * confirmation password so the password never ends up persisted as part of a setting's own value
 * (ContentService#updateSettings only ever sees {@code updates}). Only required when AppConfig's
 * settingsConfirmationEnabled flag is on — see AppConfigService#verifyConfirmationPassword.
 */
public record SettingsUpdateRequest(Map<String, String> updates, String confirmationPassword) {
}
