package com.pureeats.catalog.dto;

/** Like {@link LocationResponse} but also carries {@code isActive} - the admin list shows inactive locations too, the public one never does. */
public record LocationAdminResponse(Long id, String name, String description, boolean isPopular, boolean isActive) {
}
