package com.pureeats.catalog.dto;

import jakarta.validation.Valid;

/**
 * PUT /api/v1/admin/app-config's actual body — wraps the config fields alongside the optional
 * confirmation password so the password is never itself treated as a field to merge/persist (it's
 * checked and discarded before AppConfigService#update ever sees {@code config}). Only required
 * when settingsConfirmationEnabled is on — see AppConfigService#verifyConfirmationPassword.
 */
public record AppConfigUpdateRequest(@Valid AppConfigAdminRequest config, String confirmationPassword) {
}
