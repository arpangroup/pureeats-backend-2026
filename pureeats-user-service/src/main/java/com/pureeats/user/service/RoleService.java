package com.pureeats.user.service;

import com.pureeats.domain.common.CurrentUserContext;
import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.entity.ModelHasRole;
import com.pureeats.domain.entity.Role;
import com.pureeats.user.repository.ModelHasRoleRepository;
import com.pureeats.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resolves/assigns a user's role against the legacy Spatie-shaped schema
 * ({@code roles} + {@code model_has_roles}, morph type {@code App\User}) so
 * pre-existing seeded role data keeps working unchanged.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String USER_MORPH_TYPE = "App\\User";

    private final RoleRepository roleRepository;
    private final ModelHasRoleRepository modelHasRoleRepository;

    /**
     * A user can hold several rows in {@code model_has_roles} at once (e.g. every account starts
     * as CUSTOMER, then gains STORE_OWNER on registering a restaurant) - this resolves to the
     * single highest-privilege role for the JWT, in this fixed priority order.
     */
    private static final List<com.pureeats.domain.enums.Role> ROLE_PRIORITY = List.of(
            com.pureeats.domain.enums.Role.SUPER_ADMIN,
            com.pureeats.domain.enums.Role.ADMIN,
            com.pureeats.domain.enums.Role.EMPLOYEE,
            com.pureeats.domain.enums.Role.STORE_OWNER,
            com.pureeats.domain.enums.Role.DELIVERY,
            com.pureeats.domain.enums.Role.CUSTOMER
    );

    @Transactional(readOnly = true)
    public com.pureeats.domain.enums.Role resolveRole(Long userId) {
        List<ModelHasRole> assignments = modelHasRoleRepository.findByModelTypeAndModelId(USER_MORPH_TYPE, userId);
        if (assignments.isEmpty()) {
            return com.pureeats.domain.enums.Role.CUSTOMER;
        }
        java.util.Set<Long> roleIds = assignments.stream().map(ModelHasRole::getRoleId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<com.pureeats.domain.enums.Role> heldRoles = roleRepository.findAllById(roleIds).stream()
                .map(role -> com.pureeats.domain.enums.Role.fromLegacyName(role.getName()))
                .collect(java.util.stream.Collectors.toSet());
        return ROLE_PRIORITY.stream().filter(heldRoles::contains).findFirst().orElse(com.pureeats.domain.enums.Role.CUSTOMER);
    }

    @Transactional
    public void assignRole(Long userId, com.pureeats.domain.enums.Role role) {
        Role roleEntity = roleRepository.findByName(role.legacyName())
                .orElseGet(() -> createRole(role.legacyName()));

        boolean alreadyAssigned = modelHasRoleRepository.findByModelTypeAndModelId(USER_MORPH_TYPE, userId).stream()
                .anyMatch(a -> a.getRoleId().equals(roleEntity.getId()));
        if (!alreadyAssigned) {
            modelHasRoleRepository.save(new ModelHasRole(roleEntity.getId(), USER_MORPH_TYPE, userId));
        }
    }

    /** Whether any user currently holds {@code role} at all - used by the SUPER_ADMIN startup seeder to stay idempotent. */
    @Transactional(readOnly = true)
    public boolean anyUserHasRole(com.pureeats.domain.enums.Role role) {
        return roleRepository.findByName(role.legacyName())
                .map(entity -> modelHasRoleRepository.existsByRoleId(entity.getId()))
                .orElse(false);
    }

    /**
     * Self-registration (password signup, email signup) must never be reachable from a session
     * that already holds an elevated role - {@code SUPER_ADMIN} is seeded once at startup and
     * {@code ADMIN} accounts are meant to be provisioned by a SUPER_ADMIN, not created through the
     * public signup flow. A no-op for anonymous callers (the normal case - these endpoints are
     * public), since {@link CurrentUserContext} is only populated when a valid JWT was presented.
     */
    @Transactional(readOnly = true)
    public void assertCallerNotPrivileged() {
        Long callerId = CurrentUserContext.get();
        if (callerId == null) {
            return;
        }
        if (resolveRole(callerId).isPrivileged()) {
            throw new ForbiddenException("REGISTRATION_BLOCKED_FOR_PRIVILEGED_ROLE",
                    "Admin and Super Admin accounts cannot use the self-registration flow.");
        }
    }

    private Role createRole(String legacyName) {
        Role role = new Role();
        role.setName(legacyName);
        role.setGuardName("api");
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        return roleRepository.save(role);
    }
}
