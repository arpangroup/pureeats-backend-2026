package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.*;
import com.pureeats.order.service.DeliveryOrderService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Delivery rider", description = "Rider order workflow: accept, pickup, deliver, GPS tracking")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    @GetMapping("/orders/available")
    @Operation(summary = "List orders available to be picked up for delivery, with a payout estimate against the caller's own commission rate")
    public ApiResponse<List<DeliveryAvailableOrderResponse>> available(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(deliveryOrderService.availableOrders(principal.userId()));
    }

    @GetMapping("/orders/mine")
    @Operation(summary = "List the signed-in rider's own delivery history (every assignment, newest first)")
    public ApiResponse<List<DeliveryAssignmentResponse>> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.debug("Listing delivery history for rider {}", principal.userId());
        return ApiResponse.success(deliveryOrderService.myOrders(principal.userId()));
    }

    @GetMapping("/orders/active")
    @Operation(summary = "Orders currently assigned to the signed-in rider and not yet finished - self-accepted or assigned by an admin")
    public ApiResponse<List<DeliveryAssignmentResponse>> active(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(deliveryOrderService.activeOrders(principal.userId()));
    }

    @GetMapping("/status")
    @Operation(summary = "The signed-in rider's server-side online/offline status, including why they went offline (SELF / INACTIVITY / ADMIN)")
    public ApiResponse<RiderStatusResponse> getStatus(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(deliveryOrderService.getOnlineStatus(principal.userId()));
    }

    @PostMapping("/orders/{orderId}/accept")
    @Operation(summary = "Accept an order for delivery")
    public ApiResponse<OrderResponse> accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        log.info("Rider {} accepting order {} for delivery", principal.userId(), orderId);
        return ApiResponse.success("Order accepted for delivery", deliveryOrderService.acceptToDeliver(principal.userId(), orderId));
    }

    @PostMapping("/orders/{orderId}/pickup")
    @Operation(summary = "Mark an order as picked up from the restaurant")
    public ApiResponse<OrderResponse> pickup(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        log.info("Rider {} marking order {} as picked up", principal.userId(), orderId);
        return ApiResponse.success("Order marked as picked up", deliveryOrderService.pickedUp(principal.userId(), orderId));
    }

    @PostMapping("/orders/{orderId}/deliver")
    @Operation(summary = "Complete delivery by verifying the customer's delivery PIN - for cash on delivery, with the cash collected (any extra goes to the customer's wallet)")
    public ApiResponse<OrderResponse> deliver(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId,
                                               @Valid @RequestBody DeliverOrderRequest request) {
        log.info("Rider {} completing delivery for order {}", principal.userId(), orderId);
        return ApiResponse.success("Order delivered", deliveryOrderService.deliver(principal.userId(), orderId, request.deliveryPin(), request.cashCollected()));
    }

    @PostMapping("/orders/{orderId}/arrived")
    @Operation(summary = "Tell the customer the delivery partner has reached their location")
    public ApiResponse<OrderResponse> arrived(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        log.info("Rider {} arrived at the customer for order {}", principal.userId(), orderId);
        return ApiResponse.success("Customer notified", deliveryOrderService.arrived(principal.userId(), orderId));
    }

    @PostMapping(value = "/orders/{orderId}/pickup-photos", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo of the packed order before pickup (max 3)")
    public ApiResponse<com.pureeats.media.dto.MediaUploadResponse> uploadPickupPhoto(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                                    @PathVariable Long orderId,
                                                                                    @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        log.info("Rider {} uploading a pickup photo for order {}", principal.userId(), orderId);
        return ApiResponse.success("Photo saved", deliveryOrderService.uploadPickupPhoto(principal.userId(), orderId, file));
    }

    @GetMapping("/orders/{orderId}/pickup-photos")
    @Operation(summary = "Pickup photos taken for one of the rider's orders")
    public ApiResponse<List<PickupPhotoResponse>> pickupPhotos(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        return ApiResponse.success(deliveryOrderService.pickupPhotosForRider(principal.userId(), orderId));
    }

    @DeleteMapping("/orders/{orderId}/pickup-photos/{mediaId}")
    @Operation(summary = "Remove a pickup photo (to retake it) - only before pickup")
    public ApiResponse<Void> deletePickupPhoto(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId, @PathVariable Long mediaId) {
        deliveryOrderService.deletePickupPhoto(principal.userId(), orderId, mediaId);
        return ApiResponse.success("Photo removed", null);
    }

    @PostMapping(value = "/orders/{orderId}/delivery-photos", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo at handover to the customer (after Arrived, max 3)")
    public ApiResponse<com.pureeats.media.dto.MediaUploadResponse> uploadDeliveryPhoto(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                                      @PathVariable Long orderId,
                                                                                      @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        log.info("Rider {} uploading a delivery photo for order {}", principal.userId(), orderId);
        return ApiResponse.success("Photo saved", deliveryOrderService.uploadDeliveryPhoto(principal.userId(), orderId, file));
    }

    @GetMapping("/orders/{orderId}/delivery-photos")
    @Operation(summary = "Handover photos taken for one of the rider's orders")
    public ApiResponse<List<PickupPhotoResponse>> deliveryPhotos(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        return ApiResponse.success(deliveryOrderService.deliveryPhotosForRider(principal.userId(), orderId));
    }

    @DeleteMapping("/orders/{orderId}/delivery-photos/{mediaId}")
    @Operation(summary = "Remove a handover photo (to retake it) - only before delivery")
    public ApiResponse<Void> deleteDeliveryPhoto(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId, @PathVariable Long mediaId) {
        deliveryOrderService.deleteDeliveryPhoto(principal.userId(), orderId, mediaId);
        return ApiResponse.success("Photo removed", null);
    }

    @GetMapping("/orders/{orderId}/eta")
    @Operation(summary = "Live travel time to the customer from the partner's last position (from the restaurant before pickup)")
    public ApiResponse<DeliveryEtaResponse> eta(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long orderId) {
        return ApiResponse.success(deliveryOrderService.liveEta(principal.userId(), orderId));
    }

    @GetMapping("/activity")
    @Operation(summary = "The rider's own online/offline history (incl. auto-offline) and recent sign-ins")
    public ApiResponse<RiderActivityResponse> activity(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(deliveryOrderService.activity(principal.userId()));
    }

    @PostMapping("/gps")
    @Operation(summary = "Report the rider's current GPS location for an order")
    public ApiResponse<Void> pingGps(@Valid @RequestBody GpsPingRequest request) {
        log.debug("GPS ping received for order {}", request.orderId());
        deliveryOrderService.recordGpsPing(request);
        return ApiResponse.success("Location updated", null);
    }

    @PostMapping("/status")
    @Operation(summary = "Toggle the signed-in rider's own online/offline availability")
    public ApiResponse<Void> setStatus(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody RiderStatusRequest request) {
        log.info("Rider {} setting online status to {}", principal.userId(), request.isOnline());
        deliveryOrderService.setOnlineStatus(principal.userId(), request.isOnline());
        return ApiResponse.success(request.isOnline() ? "You're online" : "You're offline", null);
    }

    @PostMapping("/location")
    @Operation(summary = "Report the signed-in rider's own current location (independent of any single order - see /gps for order-scoped tracking)")
    public ApiResponse<Void> pingLocation(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody LocationPingRequest request) {
        log.debug("Location ping received for rider {}", principal.userId());
        deliveryOrderService.updateMyLocation(principal.userId(), request.lat(), request.lng());
        return ApiResponse.success("Location updated", null);
    }

    @GetMapping("/orders/{orderId}/gps")
    @Operation(summary = "Get the rider's last known GPS location for an order")
    public ApiResponse<GpsLocationResponse> getGps(@PathVariable Long orderId) {
        return ApiResponse.success(deliveryOrderService.getGpsLocation(orderId));
    }
}
