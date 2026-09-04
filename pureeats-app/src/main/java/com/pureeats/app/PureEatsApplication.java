package com.pureeats.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

/**
 * The single runnable module - wires every pureeats-* module together.
 * Component scan, entity scan and JPA repository scan all reach across module
 * boundaries via the shared {@code com.pureeats} package root.
 * <p>
 * {@code UserDetailsServiceAutoConfiguration} is excluded because authentication is
 * entirely JWT-based (see pureeats-user-service) - without this exclusion Spring Boot
 * spins up an unused in-memory user and logs a generated password on every startup.
 */
@SpringBootApplication(scanBasePackages = "com.pureeats", exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = {"com.pureeats.domain.entity", "com.pureeats.user.entity", "com.pureeats.notification.entity", "com.pureeats.media.entity", "com.pureeats.order.entity", "com.pureeats.catalog.entity"})
@EnableJpaRepositories(basePackages = "com.pureeats")
@Slf4j
public class PureEatsApplication {

    /**
     * Every restaurant hour, order timestamp and "is it open now" decision in this app is India-local
     * wall-clock time - this is an India-only service, there's no per-user timezone to honor. Pinning
     * the JVM default zone here means every unqualified {@code LocalDateTime.now()}/{@code new Date()}
     * across the whole app (not just the restaurant-hours code) is IST regardless of what timezone the
     * deployment container itself defaults to (commonly UTC) - deployment-environment default zone
     * bit us once already (see {@code RestaurantOpenStatusService.RESTAURANT_ZONE}), so this closes it
     * at the source instead of requiring every call site to opt in individually. Must run before
     * {@code SpringApplication.run} - a static initializer guarantees it fires at class-load, before
     * any Spring or JPA machinery (which may itself read the default zone during startup) touches it.
     */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        log.info("Starting PureEatsApplication...");
        SpringApplication.run(PureEatsApplication.class, args);
        log.info("PureEatsApplication started successfully");
    }
}
