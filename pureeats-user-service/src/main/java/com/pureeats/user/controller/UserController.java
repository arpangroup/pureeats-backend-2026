package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.ConfirmContactChangeRequest;
import com.pureeats.user.dto.LoginChallengeResponse;
import com.pureeats.user.dto.RequestContactChangeRequest;
import com.pureeats.user.dto.UpdateUserRequest;
import com.pureeats.user.dto.UserResponse;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.security.metadata.RequestMetadata;
import com.pureeats.user.security.metadata.RequestMetadataResolver;
import com.pureeats.user.service.ProfileContactChangeService;
import com.pureeats.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User profile", description = "The signed-in user's own profile")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final ProfileContactChangeService profileContactChangeService;
    private final RequestMetadataResolver requestMetadataResolver;

    @GetMapping
    @Operation(summary = "Get the signed-in user's profile")
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.debug("Fetching profile for user {}", principal.userId());
        return ApiResponse.success(userService.getProfile(principal.userId()));
    }

    @PutMapping
    @Operation(summary = "Update the signed-in user's profile")
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating profile for user {}", principal.userId());
        return ApiResponse.success("Profile updated", userService.updateProfile(principal.userId(), request));
    }

    @PostMapping("/photo")
    @Operation(summary = "Upload/replace the signed-in user's profile photo")
    public ApiResponse<UserResponse> uploadPhoto(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestParam("file") MultipartFile file) {
        log.info("Uploading profile photo for user {}", principal.userId());
        return ApiResponse.success("Photo updated", userService.updatePhoto(principal.userId(), file));
    }

    @PostMapping("/phone/otp")
    @Operation(summary = "Start verification of a new phone number for the signed-in user")
    public ApiResponse<LoginChallengeResponse> requestPhoneChange(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @Valid @RequestBody RequestContactChangeRequest request,
                                                                    HttpServletRequest httpRequest) {
        log.info("Phone change requested for user {}", principal.userId());
        return ApiResponse.success(profileContactChangeService.requestPhoneChange(principal.userId(), request.destination(), metadata(httpRequest)));
    }

    @PostMapping("/phone/verify")
    @Operation(summary = "Verify the OTP and apply the new phone number")
    public ApiResponse<UserResponse> confirmPhoneChange(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody ConfirmContactChangeRequest request) {
        return ApiResponse.success("Phone number updated", profileContactChangeService.confirmPhoneChange(principal.userId(), request.challengeId(), request.otp()));
    }

    @PostMapping("/email/otp")
    @Operation(summary = "Start verification of a new email address for the signed-in user")
    public ApiResponse<LoginChallengeResponse> requestEmailChange(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @Valid @RequestBody RequestContactChangeRequest request,
                                                                    HttpServletRequest httpRequest) {
        log.info("Email change requested for user {}", principal.userId());
        return ApiResponse.success(profileContactChangeService.requestEmailChange(principal.userId(), request.destination(), metadata(httpRequest)));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Verify the OTP and apply the new email address")
    public ApiResponse<UserResponse> confirmEmailChange(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody ConfirmContactChangeRequest request) {
        return ApiResponse.success("Email updated", profileContactChangeService.confirmEmailChange(principal.userId(), request.challengeId(), request.otp()));
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        return requestMetadataResolver.resolve(request);
    }
}
