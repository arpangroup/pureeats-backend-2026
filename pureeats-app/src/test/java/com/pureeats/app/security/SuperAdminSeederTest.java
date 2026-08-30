package com.pureeats.app.security;

import com.pureeats.domain.enums.Role;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.service.RoleService;
import com.pureeats.user.seeder.SuperAdminSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SuperAdminSeeder} runs automatically as an {@code ApplicationRunner} when the Spring
 * context boots - by the time any test method executes, exactly one SUPER_ADMIN should already
 * exist. Re-invoking it manually must be a no-op (idempotent across app restarts).
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SuperAdminSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SuperAdminSeeder superAdminSeeder;

    @Test
    void aSuperAdminAccountExistsAfterStartup() {
        assertTrue(roleService.anyUserHasRole(Role.SUPER_ADMIN));
    }

    @Test
    void reRunningTheSeederNeverCreatesASecondSuperAdmin() {
        long usersBefore = userRepository.count();

        superAdminSeeder.run(null);
        superAdminSeeder.run(null);

        assertEquals(usersBefore, userRepository.count());
        assertTrue(roleService.anyUserHasRole(Role.SUPER_ADMIN));
    }
}
