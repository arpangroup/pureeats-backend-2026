package com.pureeats.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTest {

    @Test
    void superAdminAndAdminAreConsideredPrivileged() {
        assertTrue(Role.SUPER_ADMIN.isPrivileged());
        assertTrue(Role.ADMIN.isPrivileged());
    }

    @Test
    void otherRolesAreNotPrivileged() {
        for (Role role : Role.values()) {
            if (role != Role.SUPER_ADMIN && role != Role.ADMIN) {
                assertFalse(role.isPrivileged(), role + " should not be privileged");
            }
        }
    }

    @Test
    void legacyNameRoundTripsForEveryRole() {
        for (Role role : Role.values()) {
            assertEquals(role, Role.fromLegacyName(role.legacyName()));
        }
    }

    @Test
    void superAdminAuthorityMatchesSpringSecurityConvention() {
        assertEquals("ROLE_SUPER_ADMIN", Role.SUPER_ADMIN.authority());
    }
}
