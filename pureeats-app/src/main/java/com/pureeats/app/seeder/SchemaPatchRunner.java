package com.pureeats.app.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off, idempotent fixes for columns whose nullability changed in code after the table already
 * existed in a developer's database - {@code ddl-auto=update} only ever adds tables/columns, it
 * never relaxes an existing NOT NULL constraint, so a dev DB created before {@code Slide} gained
 * {@code restaurantCategorySliderId} (and {@code promoSliderId} became optional) is still stuck
 * with the old constraint and every category-slider slide save fails with a DB error. Runs before
 * the demo seeders ({@code @Order(0)}); every statement is wrapped so a database that's already
 * correct (or isn't MySQL/MariaDB) just logs and moves on instead of failing startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(0)
public class SchemaPatchRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        relaxNotNull("slides", "promo_slider_id", "INT");
    }

    private void relaxNotNull(String table, String column, String sqlType) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + sqlType + " NULL");
            log.info("Schema patch applied: {}.{} is now nullable", table, column);
        } catch (Exception e) {
            log.debug("Schema patch skipped for {}.{} ({})", table, column, e.getMessage());
        }
    }
}
