package com.pureeats.user.controller;

import com.pureeats.domain.common.CurrentUserContext;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.*;
import com.pureeats.user.security.metadata.RequestMetadata;
import com.pureeats.user.security.metadata.RequestMetadataResolver;
import com.pureeats.user.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "The OTP-challenge signup/login flow, refresh tokens, and logout")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RequestMetadataResolver requestMetadataResolver;

    @PostMapping("/register")
    @Operation(summary = "Create an account by email and send a verification OTP")
    public ApiResponse<LoginChallengeResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success(authenticationService.signup(request, metadata(httpRequest)));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Start an OTP-based login challenge for a phone or email")
    public ApiResponse<LoginChallengeResponse> initiateOtpLogin(@Valid @RequestBody LoginChallengeRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success(authenticationService.initiateLogin(request, metadata(httpRequest)));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify a challenge's OTP and receive an access + refresh token")
    public ApiResponse<AuthTokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success("Authentication successful.",
                authenticationService.verifyOtp(request.challengeId(), request.otp(), metadata(httpRequest)));
    }

    @PostMapping("/otp/resend")
    @Operation(summary = "Resend the OTP for an existing challenge")
    public ApiResponse<ResendOtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success(authenticationService.resendOtp(request.challengeId(), metadata(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access + refresh token (rotated)")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success(authenticationService.refresh(request.refreshToken(), metadata(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a single refresh token / session")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        authenticationService.logout(request.refreshToken(), metadata(httpRequest));
        return ApiResponse.success("Logged out successfully", null);
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke every session for the currently authenticated user")
    public ApiResponse<Void> logoutAll(HttpServletRequest httpRequest) {
        authenticationService.logoutAll(CurrentUserContext.get(), metadata(httpRequest));
        return ApiResponse.success("Logged out of all devices", null);
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        return requestMetadataResolver.resolve(request);
    }
}
