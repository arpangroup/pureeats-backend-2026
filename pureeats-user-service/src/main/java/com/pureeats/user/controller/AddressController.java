package com.pureeats.user.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.user.dto.AddressRequest;
import com.pureeats.user.dto.AddressResponse;
import com.pureeats.user.security.AuthenticatedUser;
import com.pureeats.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "The signed-in user's saved delivery addresses")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "List the signed-in user's addresses")
    public ApiResponse<List<AddressResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(addressService.list(principal.userId()));
    }

    @PostMapping
    @Operation(summary = "Save a new address")
    public ApiResponse<AddressResponse> save(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success("Address saved", addressService.save(principal.userId(), request));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Edit an existing address")
    public ApiResponse<AddressResponse> edit(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable Long addressId,
                                              @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success("Address updated", addressService.edit(principal.userId(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete an address")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long addressId) {
        addressService.delete(principal.userId(), addressId);
        return ApiResponse.success("Address deleted", null);
    }

    @PatchMapping("/{addressId}/default")
    @Operation(summary = "Mark an address as the default")
    public ApiResponse<Void> setDefault(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long addressId) {
        addressService.setDefault(principal.userId(), addressId);
        return ApiResponse.success("Default address updated", null);
    }
}
