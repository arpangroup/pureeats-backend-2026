package com.pureeats.app.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off, idempotent fixes for constraints that changed in code after the table already existed
 * in a database - {@code ddl-auto=update} only ever adds tables/columns, it never relaxes an
 * existing NOT NULL constraint or widens an existing CHECK constraint, so a database created
 * before a column's rules changed is still stuck with the old ones. {@code relaxNotNull} predates
 * the move to Postgres (MySQL/MariaDB syntax - a database created before {@code Slide} gained
 * {@code restaurantCategorySliderId} and {@code promoSliderId} became optional); {@code
 * refreshCheckConstraint} is Postgres syntax, added when {@code AccountStatus} gained {@code
 * DELETED} - Hibernate had already generated {@code users_account_status_check} from the
 * then-current enum values when the column was first created, and {@code ddl-auto=update} left
 * that CHECK exactly as it was, so every self-service account deletion failed with a
 * ConstraintViolationException until this ran. Runs before the demo seeders ({@code @Order(0)});
 * every statement is wrapped so a database that's already correct (or doesn't have the constraint
 * in the first place) just logs and moves on instead of failing startup.
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
        // account_status is nullable (AuthenticationService#assertAccountUsable treats a null
        // status as ACTIVE for exactly this reason - older rows predate the column) - the
        // constraint must allow NULL too, or re-adding it would fail outright against any
        // existing NULL row, same as Hibernate's own originally-generated version did.
        refreshCheckConstraint("users", "users_account_status_check", "account_status",
                "account_status IN ('ACTIVE', 'TEMPORARILY_LOCKED', 'BLOCKED', 'DISABLED', 'DELETED') OR account_status IS NULL");
    }

    private void relaxNotNull(String table, String column, String sqlType) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + sqlType + " NULL");
            log.info("Schema patch applied: {}.{} is now nullable", table, column);
        } catch (Exception e) {
            log.debug("Schema patch skipped for {}.{} ({})", table, column, e.getMessage());
        }
    }

    /** Drops and re-adds a named CHECK constraint with the given (full) condition - safe to run
     * on every startup: an already-current constraint just means the DROP succeeds and the
     * (identical) ADD succeeds too, both cheap no-ops. */
    private void refreshCheckConstraint(String table, String constraintName, String column, String conditionSql) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName + " CHECK (" + conditionSql + ")");
            log.info("Schema patch applied: {}.{} CHECK constraint refreshed ({})", table, column, conditionSql);
        } catch (Exception e) {
            log.debug("Schema patch skipped for {}.{} CHECK constraint ({})", table, column, e.getMessage());
        }
    }
}
