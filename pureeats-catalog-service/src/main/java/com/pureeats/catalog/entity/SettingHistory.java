package com.pureeats.catalog.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One row per changed setting field — the old value, the new value, and who changed it. Same shape
 * and convention as {@link RestaurantAuditLog}, just not scoped to one restaurant: `source`
 * distinguishes which store the field lives in (the generic key/value {@code settings} table vs the
 * structured {@code app_config} blob), since the two use different key-naming conventions
 * (snake_case settings keys vs camelCase AppConfig field names) and it's useful to filter by.
 */
@Entity
@Table(name = "setting_history", indexes = {
        @Index(name = "idx_setting_history_field_key", columnList = "field_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** "SETTING" (the generic key/value store) or "APP_CONFIG" (the structured blob) — see SettingHistoryService's constants. */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "field_key", nullable = false, length = 128)
    private String fieldKey;

    /** Redacted to a fixed placeholder rather than stored in the clear for secret/password fields — see SettingHistoryService#isSensitive. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
