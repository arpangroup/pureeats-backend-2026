package com.pureeats.notification.repository;

import com.pureeats.domain.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Own Spring Data interface over the shared {@code settings} key/value table (see
 * {@code com.pureeats.catalog.repository.SettingRepository} for catalog-service's identical
 * interface over the same table) - declared here rather than reused across modules so
 * {@code pureeats-notification-service} doesn't need a new dependency on catalog-service just to
 * store its own routing config. Named distinctly (not just {@code SettingRepository}) because
 * Spring Data derives bean names from the interface's simple name, and two different
 * {@code SettingRepository} interfaces in different packages would otherwise collide when both are
 * component-scanned into the same application context (as they are here, in pureeats-app).
 */
public interface NotificationSettingRepository extends JpaRepository<Setting, Long> {
    Optional<Setting> findByKey(String key);
}
