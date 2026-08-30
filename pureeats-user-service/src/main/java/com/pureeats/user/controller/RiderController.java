package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.RiderProfileRequest;
import com.pureeats.user.dto.RiderProfileResponse;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.service.RiderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/rider-profile")
@RequiredArgsConstructor
@Tag(name = "Rider onboarding", description = "Self-serve delivery-rider profile creation")
@SecurityRequirement(name = "bearerAuth")
public class RiderController {

    private final RiderService riderService;

    @PostMapping
    @Operation(summary = "Register the signed-in user as a delivery rider")
    public ApiResponse<RiderProfileResponse> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @Valid @RequestBody RiderProfileRequest request) {
        return ApiResponse.success("Rider profile created - log in again to receive a DELIVERY-role token",
                riderService.registerAsRider(principal.userId(), request));
    }

    @GetMapping
    @Operation(summary = "Get the signed-in rider's profile")
    public ApiResponse<RiderProfileResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(riderService.getProfile(principal.userId()));
    }
}
