package com.pureeats.catalog.dto;

import java.util.List;

/**
 * One editable setting — the backend's single source of truth for what a field is called, what
 * kind of input it needs, and what it defaults to. The admin panel renders purely off this (see
 * GET /api/v1/admin/settings/schema, SettingSchemaService): add a field here and it appears in the
 * UI on its own, no frontend change needed. `key` is what actually gets stored (as one row in the
 * generic `settings` table) and is what PUT /api/v1/admin/settings expects as a map key — that
 * endpoint checks every incoming key against this same registry before saving anything (see
 * ContentService#updateSettings), so a value can never be saved that this class doesn't already
 * know about.
 */
public record SettingFieldDefinition(
        String key,
        String label,
        /** One of: text | password | number | email | url | textarea | boolean | dropdown | radio — mirrors the admin panel's SettingFieldType. */
        String fieldType,
        String defaultValue,
        String placeholder,
        List<SettingOptionDefinition> options,
        /** Short neutral note rendered below the field. */
        String info,
        /** Rendered in amber — for anything risky (secrets, irreversible effects). */
        String warning,
        SettingLinkDefinition link,
        boolean required
) {
    public static SettingFieldDefinition of(String key, String label, String fieldType, String defaultValue) {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, null, null, null, null, null, false);
    }

    public SettingFieldDefinition placeholder(String placeholder) {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, placeholder, options, info, warning, link, required);
    }

    public SettingFieldDefinition info(String info) {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, placeholder, options, info, warning, link, required);
    }

    public SettingFieldDefinition warning(String warning) {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, placeholder, options, info, warning, link, required);
    }

    public SettingFieldDefinition link(String linkLabel, String href) {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, placeholder, options, info, warning, new SettingLinkDefinition(linkLabel, href), required);
    }

    public SettingFieldDefinition options(SettingOptionDefinition... opts) {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, placeholder, List.of(opts), info, warning, link, required);
    }

    public SettingFieldDefinition markRequired() {
        return new SettingFieldDefinition(key, label, fieldType, defaultValue, placeholder, options, info, warning, link, true);
    }
}
