package com.pureeats.user.controller;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.ForgotPasswordRequest;
import com.pureeats.user.dto.OtpSentResponse;
import com.pureeats.user.dto.ResetPasswordRequest;
import com.pureeats.user.dto.VerifyResetOtpRequest;
import com.pureeats.user.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
@Tag(name = "Password reset", description = "Email-based forgot-password flow")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot")
    @Operation(summary = "Send a password reset code to the account email")
    public ApiResponse<OtpSentResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Password reset requested for {}", PiiMaskUtil.maskEmail(request.email()));
        return ApiResponse.success(passwordResetService.sendResetCode(request.email()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify a password reset code")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyResetOtpRequest request) {
        log.debug("Password reset code verification requested for {}", PiiMaskUtil.maskEmail(request.email()));
        passwordResetService.verifyCode(request.email(), request.code());
        return ApiResponse.success("Code verified", null);
    }

    @PostMapping("/reset")
    @Operation(summary = "Set a new password using a verified reset code")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Password reset completion requested for {}", PiiMaskUtil.maskEmail(request.email()));
        passwordResetService.resetPassword(request.email(), request.code(), request.newPassword());
        return ApiResponse.success("Password updated successfully", null);
    }
}
