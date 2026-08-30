package com.pureeats.app.security;

import com.pureeats.domain.enums.Role;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for a role-rename bug: {@code Role.RESTAURANT_OWNER} was renamed to
 * {@code Role.STORE_OWNER}, but {@code SecurityConfig} kept checking
 * {@code .hasRole("RESTAURANT_OWNER")} - a plain string that no longer matched any authority a
 * STORE_OWNER JWT actually carries, silently 403-ing every store-owner-role user out of their own
 * endpoints. Confirms the fix: a STORE_OWNER token can reach {@code /api/v1/store-owner/**}, and a
 * CUSTOMER token (negative control) still cannot.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreOwnerRoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void storeOwnerRoleCanReachStoreOwnerEndpoints() throws Exception {
        String token = tokenFor(Role.STORE_OWNER);

        mockMvc.perform(get("/api/v1/store-owner/restaurants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void customerRoleIsStillRejectedFromStoreOwnerEndpoints() throws Exception {
        String token = tokenFor(Role.CUSTOMER);

        mockMvc.perform(get("/api/v1/store-owner/restaurants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String tokenFor(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(999_999L, "Test User", "test@example.com", "9999999999", role, null);
        return jwtTokenProvider.generateToken(principal);
    }
}
