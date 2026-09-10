package com.pureeats.app.notification.controller;

import com.pureeats.app.notification.dto.TestEmailRequest;
import com.pureeats.app.notification.dto.TestPushRequest;
import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.notification.dto.NotificationRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.provider.EmailProvider;
import com.pureeats.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lets an admin verify their email/push configuration actually works end-to-end from the Settings
 * page, without having to trigger a real OTP or order flow first. Email goes straight through
 * {@link EmailProvider} (bypassing the templated {@link NotificationService} path - there's no
 * template file for a one-off test message, see NotificationType#TEST); push goes through the
 * normal {@link NotificationService}, since PushNotificationSender never needs a template.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/notifications/test")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Notification Test", description = "Send a one-off test email/push from the admin panel - ADMIN or SUPER_ADMIN only")
public class AdminNotificationTestController {

    private final EmailProvider emailProvider;
    private final NotificationService notificationService;

    @PostMapping("/email")
    @Operation(summary = "Send a test email to confirm the email settings/provider work")
    public ApiResponse<Void> sendTestEmail(@Valid @RequestBody TestEmailRequest request) {
        log.info("Admin sending test email to {}", PiiMaskUtil.maskEmail(request.to()));
        String html = "<p>This is a test email from your PureEats admin panel.</p>"
                + "<p>If you're reading this, your email settings are configured correctly.</p>";
        String text = "This is a test email from your PureEats admin panel.\n\n"
                + "If you're reading this, your email settings are configured correctly.";
        NotificationResult result = emailProvider.send(request.to(), "PureEats test email", html, text);
        if (!result.success()) {
            throw new BadRequestException("Could not send test email: " + result.failureReason());
        }
        return ApiResponse.success("Test email sent to " + request.to(), null);
    }

    @PostMapping("/push")
    @Operation(summary = "Send a test push notification to every active device a given user has registered")
    public ApiResponse<Void> sendTestPush(@Valid @RequestBody TestPushRequest request) {
        log.info("Admin sending test push notification to user {}", request.userId());
        String title = request.title() != null && !request.title().isBlank() ? request.title() : "PureEats test notification";
        String body = request.body() != null && !request.body().isBlank() ? request.body() : "This is a test push notification sent from the admin panel.";
        NotificationResult result = notificationService.send(new NotificationRequest(
                NotificationType.TEST, NotificationChannel.PUSH, null, request.userId(),
                Map.of("title", title, "body", body)));
        if (!result.success()) {
            throw new BadRequestException("Could not send test push notification: " + result.failureReason());
        }
        return ApiResponse.success("Test push notification sent", null);
    }
}
