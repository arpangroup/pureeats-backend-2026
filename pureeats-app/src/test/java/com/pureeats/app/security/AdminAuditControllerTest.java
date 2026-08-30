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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms both authorization layers on {@code AdminAuditController}: the pre-existing
 * {@code SecurityConfig} URL rule for {@code /api/v1/admin/**}, and the new class-level
 * {@code @PreAuthorize} - a CUSTOMER token must be rejected and an ADMIN/SUPER_ADMIN token must
 * succeed, for every one of the seven audit endpoints. Also spot-checks that the OTP-challenge
 * view never serializes the OTP hash.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RoleService roleService;

    private static final String[] ENDPOINTS = {
            "/api/v1/admin/audit-logs",
            "/api/v1/admin/login-history",
            "/api/v1/admin/otp-challenges",
            "/api/v1/admin/rate-limit-buckets",
            "/api/v1/admin/security-blocklist",
            "/api/v1/admin/user-devices",
            "/api/v1/admin/user-sessions",
    };

    @Test
    void customerIsRejectedFromEveryAuditEndpoint() throws Exception {
        String token = tokenFor(666_701L, Role.CUSTOMER);
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanReachEveryAuditEndpoint() throws Exception {
        String token = tokenFor(666_702L, Role.ADMIN);
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(0));
        }
    }

    @Test
    void superAdminCanReachEveryAuditEndpoint() throws Exception {
        String token = tokenFor(666_703L, Role.SUPER_ADMIN);
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void otpChallengeViewNeverExposesTheOtpHash() throws Exception {
        String token = tokenFor(666_704L, Role.ADMIN);
        mockMvc.perform(get("/api/v1/admin/otp-challenges").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    org.junit.jupiter.api.Assertions.assertFalse(body.contains("otpHash"),
                            "otp-challenges response must never contain the OTP hash field");
                    org.junit.jupiter.api.Assertions.assertFalse(body.contains("refreshTokenHash"),
                            "no admin audit response should ever contain a refresh token hash");
                });
    }

    private String tokenFor(Long userId, Role role) {
        roleService.assignRole(userId, role);
        AuthenticatedUser principal = new AuthenticatedUser(userId, "Audit Test User", "audit-test@example.com", "9777777777", role, null);
        return jwtTokenProvider.generateToken(principal);
    }
}
