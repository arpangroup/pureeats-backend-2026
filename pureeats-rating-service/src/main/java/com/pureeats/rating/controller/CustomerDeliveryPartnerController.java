package com.pureeats.rating.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.rating.dto.DeliveryPartnerProfileResponse;
import com.pureeats.rating.service.DeliveryPartnerProfileService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Customer-facing delivery partner profile for an order (any authenticated user; ownership checked in the service). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer order endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CustomerDeliveryPartnerController {

    private final DeliveryPartnerProfileService deliveryPartnerProfileService;

    @GetMapping("/api/v1/orders/{orderId}/delivery-partner")
    @Operation(summary = "Profile of the delivery partner on the caller's own order: photo, rating + breakdown, completed trips, compliments, recent reviews")
    public ApiResponse<DeliveryPartnerProfileResponse> deliveryPartner(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        return ApiResponse.success(deliveryPartnerProfileService.forCustomerOrder(principal.userId(), orderId));
    }
}
