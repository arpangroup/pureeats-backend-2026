package com.pureeats.app.security;

import com.pureeats.domain.enums.Role;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.security.JwtTokenProvider;
import com.pureeats.user.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SUPER_ADMIN and ADMIN accounts are provisioned out-of-band (seeder / future admin panel) and
 * must never be reachable through the public self-registration endpoints - see
 * {@code RoleService.assertCallerNotPrivileged}. Both {@code /register} (password) and
 * {@code /signup} (OTP-challenge) call the same guard.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationPrivilegeGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RoleService roleService;

    @Test
    void adminCannotUseThePasswordRegistrationEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + tokenFor(777_701L, Role.ADMIN))
                        .contentType("application/json")
                        .content("""
                                {"name":"Sneaky Admin","email":"sneaky-admin@example.com","phone":"9000000099","password":"secret123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCannotUseTheOtpSignupEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .header("Authorization", "Bearer " + tokenFor(777_702L, Role.SUPER_ADMIN))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Sneaky Super Admin","email":"sneaky-super@example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCustomerRegistrationStillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"Normal Customer","email":"normal-customer-xyz@example.com","phone":"9000000098","password":"secret123"}
                                """))
                .andExpect(status().isCreated());
    }

    /**
     * The guard re-checks the caller's role against {@code model_has_roles} (the DB, current
     * source of truth), not the role claim embedded in the JWT - so the test seeds a real role
     * assignment for a throwaway userId before minting a token for that same id, otherwise the
     * DB lookup would (correctly) find no assignment and resolve to CUSTOMER regardless of what
     * the token claims.
     */
    private String tokenFor(Long userId, Role role) {
        roleService.assignRole(userId, role);
        AuthenticatedUser principal = new AuthenticatedUser(userId, "Privileged Test User", "privileged@example.com", "9888888888", role, null);
        return jwtTokenProvider.generateToken(principal);
    }
}
