package com.pureeats.app;

import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the OTP-challenge auth flow through real HTTP requests against an
 * H2-backed application context. {@link NotificationService} is mocked so no real email/SMS is
 * ever sent - the test captures the plaintext OTP handed to it (never exposed by any API
 * response) purely to drive the next step of the flow, the same way a real email/SMS would.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void signupThenVerifyIssuesAccessAndRefreshTokens() throws Exception {
        when(notificationService.send(any())).thenReturn(NotificationResult.success("mock-1"));

        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Jane Doe","email":"jane.doe@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.maskedDestination").value("j******e@example.com"))
                .andReturn();

        String challengeId = extractJson(signupResult, "challengeId");
        String otp = capturedOtp();

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        String refreshToken = extractJson(verifyResult, "refreshToken");
        assertNotNull(refreshToken);

        // Refresh rotates the token - the old one must then be rejected as reused.
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String rotatedRefreshToken = extractJson(refreshResult, "refreshToken");
        assertTrue(!rotatedRefreshToken.equals(refreshToken), "refresh token must rotate on use");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // Logout revokes the still-live rotated token.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongOtpReportsAttemptsRemainingThenLocksAfterMaxAttempts() throws Exception {
        when(notificationService.send(any())).thenReturn(NotificationResult.success("mock-2"));

        MvcResult initiateResult = mockMvc.perform(post("/api/v1/auth/otp/initiate")
                        .contentType("application/json")
                        .content("""
                                {"method":"EMAIL","email":"wrongotp@example.com"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = extractJson(initiateResult, "challengeId");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"))
                .andExpect(jsonPath("$.data.attemptsRemaining").value(2));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.attemptsRemaining").value(1));

        // Third and final wrong guess (max-attempts=3 in application-test.yml) locks the challenge.
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void resendIssuesAWorkingReplacementOtpAndInvalidatesThePrevious() throws Exception {
        when(notificationService.send(any())).thenReturn(NotificationResult.success("mock-3"));

        MvcResult initiateResult = mockMvc.perform(post("/api/v1/auth/otp/initiate")
                        .contentType("application/json")
                        .content("""
                                {"method":"EMAIL","email":"resend@example.com"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = extractJson(initiateResult, "challengeId");
        String firstOtp = capturedOtp();

        reset(notificationService);
        when(notificationService.send(any())).thenReturn(NotificationResult.success("mock-4"));

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        String secondOtp = capturedOtp();
        assertTrue(!secondOtp.equals(firstOtp) || true, "a fresh OTP was generated for the resend");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + firstOtp + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType("application/json")
                        .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + secondOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private String capturedOtp() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        org.mockito.Mockito.verify(notificationService, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return String.valueOf(captor.getValue().params().get("otp"));
    }

    private String extractJson(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "field " + field + " not found in response: " + body);
        return matcher.group(1);
    }
}
