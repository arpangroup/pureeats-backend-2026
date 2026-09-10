package com.pureeats.catalog.dto;

import java.util.List;

/** One SectionCard's worth of related fields within a settings section (e.g. "Firebase Cloud Messaging" within "Push Notifications"). */
public record SettingGroupDefinition(String title, String description, String icon, List<SettingFieldDefinition> fields) {
    public static SettingGroupDefinition of(String title, String icon, List<SettingFieldDefinition> fields) {
        return new SettingGroupDefinition(title, null, icon, fields);
    }

    public static SettingGroupDefinition of(String title, String description, String icon, List<SettingFieldDefinition> fields) {
        return new SettingGroupDefinition(title, description, icon, fields);
    }
}
