package com.pureeats.notification.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.notification.dto.NotificationRoutingConfig;
import com.pureeats.notification.service.NotificationRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Lets an admin choose, without a deploy, which channel(s) fire for OTP fan-out and for each
 * order-status recipient role - see {@link NotificationRoutingService}. Access is restricted by
 * pureeats-app's central {@code SecurityConfig}, which maps every {@code /api/v1/admin/**} path to
 * ADMIN/SUPER_ADMIN only - this module has no direct dependency on spring-security to enforce it
 * locally via {@code @PreAuthorize}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/notification-routing")
@RequiredArgsConstructor
@Tag(name = "Notification Routing", description = "Admin config for which channel(s) fire for which notification/recipient")
public class NotificationRoutingController {

    private final NotificationRoutingService notificationRoutingService;

    @GetMapping
    @Operation(summary = "Get the current notification routing config")
    public ApiResponse<NotificationRoutingConfig> get() {
        return ApiResponse.success(notificationRoutingService.getConfig());
    }

    @PutMapping
    @Operation(summary = "Update the notification routing config")
    public ApiResponse<NotificationRoutingConfig> update(@Valid @RequestBody NotificationRoutingConfig request) {
        log.info("Admin updating notification routing config");
        return ApiResponse.success("Notification routing config updated", notificationRoutingService.update(request));
    }
}
