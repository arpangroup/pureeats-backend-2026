package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.*;
import com.pureeats.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, password login and OTP login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer account")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Registered successfully", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email/phone + password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Logged in successfully", authService.login(request));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send a login OTP to a phone number")
    public ApiResponse<OtpSentResponse> sendLoginOtp(@Valid @RequestBody SendOtpRequest request) {
        return ApiResponse.success(authService.sendLoginOtp(request.phone()));
    }

    @PostMapping("/otp/login")
    @Operation(summary = "Login (or auto-register) using a verified phone OTP")
    public ApiResponse<AuthResponse> loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        return ApiResponse.success("Logged in successfully", authService.loginWithOtp(request));
    }
}
