/*
package com.pureeats.domain.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        CustomUserPrincipal principal =
                (CustomUserPrincipal) authentication.getPrincipal();

        return Optional.of(principal.getUserId());
    }
}

Then register it:

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return new AuditorAwareImpl();
    }
}

Your entity would then use:

@CreatedBy
@Column(nullable = false, updatable = false)
private Long createdBy;

@LastModifiedBy
@Column(nullable = false)
private Long lastUpdatedBy;*/
