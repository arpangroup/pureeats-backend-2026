package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.UpdateUserRequest;
import com.pureeats.user.dto.UserResponse;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User profile", description = "The signed-in user's own profile")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get the signed-in user's profile")
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(userService.getProfile(principal.userId()));
    }

    @PutMapping
    @Operation(summary = "Update the signed-in user's profile")
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("Profile updated", userService.updateProfile(principal.userId(), request));
    }
}
