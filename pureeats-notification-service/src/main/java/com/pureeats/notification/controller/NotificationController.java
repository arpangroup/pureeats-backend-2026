package com.pureeats.notification.controller;

import com.pureeats.domain.common.CurrentUserContext;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.notification.dto.AlertResponse;
import com.pureeats.notification.dto.SavePushTokenRequest;
import com.pureeats.notification.service.AlertService;
import com.pureeats.notification.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reads the caller's id from {@link CurrentUserContext} (set by the JWT filter in
 * pureeats-user-service) rather than {@code @AuthenticationPrincipal}, so this module
 * doesn't need a compile dependency on pureeats-user-service.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Push-token registration and in-app alerts")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final PushTokenService pushTokenService;
    private final AlertService alertService;

    @PostMapping("/push-token")
    @Operation(summary = "Register/refresh this device's push notification token")
    public ApiResponse<Void> savePushToken(@Valid @RequestBody SavePushTokenRequest request) {
        pushTokenService.save(CurrentUserContext.get(), request.token());
        return ApiResponse.success("Push token saved", null);
    }

    @GetMapping
    @Operation(summary = "List the signed-in user's recent notifications (last 7 days, max 20)")
    public ApiResponse<List<AlertResponse>> list() {
        return ApiResponse.success(alertService.listRecent(CurrentUserContext.get()));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ApiResponse<Void> markAllRead() {
        alertService.markAllRead(CurrentUserContext.get());
        return ApiResponse.success("All notifications marked as read", null);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        alertService.markRead(CurrentUserContext.get(), id);
        return ApiResponse.success("Notification marked as read", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one notification")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        alertService.delete(CurrentUserContext.get(), id);
        return ApiResponse.success("Notification deleted", null);
    }
}
