package com.pureeats.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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

    public static void main(String[] args) {
        log.info("Starting PureEatsApplication...");
        SpringApplication.run(PureEatsApplication.class, args);
        log.info("PureEatsApplication started successfully");
    }
}
