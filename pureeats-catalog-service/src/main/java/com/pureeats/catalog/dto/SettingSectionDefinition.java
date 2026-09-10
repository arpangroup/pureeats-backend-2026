package com.pureeats.catalog.dto;

import java.util.List;

/** One tab in the admin Settings page. `key` matches the frontend's tab-selection key (e.g. "social-login") — the admin panel builds its whole tab list from this array now, so adding a section here adds a tab with no frontend change either. */
public record SettingSectionDefinition(String key, String title, String icon, List<SettingGroupDefinition> groups) {
}
