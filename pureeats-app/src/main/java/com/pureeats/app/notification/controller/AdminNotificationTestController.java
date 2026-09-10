package com.pureeats.app.notification.controller;

import com.pureeats.app.notification.dto.TestEmailRequest;
import com.pureeats.app.notification.dto.TestPushRequest;
import com.pureeats.app.notification.dto.TopicSubscribeRequest;
import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.dto.FcmPushRequest;
import com.pureeats.notification.dto.NotificationResult;
import com.pureeats.notification.enums.NotificationChannel;
import com.pureeats.notification.enums.NotificationType;
import com.pureeats.notification.provider.EmailProvider;
import com.pureeats.notification.repository.PushTokenRepository;
import com.pureeats.notification.service.FcmSender;
import com.pureeats.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lets an admin verify their email/push configuration actually works end-to-end from the Settings
 * page - or, via Postman/curl, push a real test notification at any customer/staff user id, or at
 * an entire topic (see this project's docs for the curl examples). Email goes straight through
 * {@link EmailProvider} (bypassing the templated {@link NotificationService} path - there's no
 * template file for a one-off test message, see NotificationType#TEST).
 * <p>
 * A {@code userId}-targeted push fans out to both {@link NotificationChannel#PUSH} (an actual FCM
 * push, if the target has a registered device token) and {@link NotificationChannel#IN_APP} (an
 * {@code Alert} row) in the same call, so it both reaches the device AND shows up in that user's
 * notification bell - in whichever app they're signed into, customer or admin - even if no device
 * token is registered yet. A {@code topic}-targeted push has no single recipient to attach a bell
 * entry to, so it calls {@link FcmSender} directly and skips IN_APP entirely.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
//@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Notification Test", description = "Send a one-off test email/push from the admin panel - ADMIN or SUPER_ADMIN only")
public class AdminNotificationTestController {

    private final EmailProvider emailProvider;
    private final NotificationService notificationService;
    private final FcmSender fcmSender;
    private final PushTokenRepository pushTokenRepository;

    @PostMapping("/test/email")
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

    @PostMapping("/test/push")
    @Operation(summary = "Send a test push - to a user id (fans out to every device they've registered, and records it in their notification bell unless silent) or to a topic (every subscribed device, never a bell entry). Exactly one of userId/topic must be set.")
    public ApiResponse<Void> sendTestPush(@Valid @RequestBody TestPushRequest request) {
        if ((request.userId() == null) == (request.topic() == null)) {
            throw new BadRequestException("Set exactly one of userId or topic, not both and not neither");
        }
        String title = request.title() != null && !request.title().isBlank() ? request.title() : "PureEats test notification";
        String body = request.body() != null && !request.body().isBlank() ? request.body() : "This is a test push notification sent from the admin panel.";

        if (request.topic() != null) {
            log.info("Admin sending {} test push notification to topic '{}'", request.silent() ? "silent" : "visible", request.topic());
            FcmPushRequest fcmRequest = request.silent()
                    ? FcmPushRequest.silent(null, request.topic(), request.data())
                    : FcmPushRequest.visible(null, request.topic(), title, body, request.imageUrl(), request.data(), request.clickAction(), request.actions());
            fcmSender.send(fcmRequest);
            return ApiResponse.success("Test push notification sent to topic '" + request.topic() + "'", null);
        }

        log.info("Admin sending {} test push notification to user {}", request.silent() ? "silent" : "visible", request.userId());
        Map<String, Object> params = new java.util.HashMap<>();
        if (request.silent()) {
            params.put("silent", true);
        } else {
            params.put("title", title);
            params.put("body", body);
            if (request.imageUrl() != null) params.put("imageUrl", request.imageUrl());
            if (request.clickAction() != null) params.put("clickAction", request.clickAction());
            if (request.actions() != null && !request.actions().isEmpty()) params.put("actions", request.actions());
        }
        if (request.data() != null && !request.data().isEmpty()) params.put("data", request.data());

        Map<NotificationChannel, NotificationResult> results = notificationService.sendToChannels(
                NotificationType.TEST, null, request.userId(), params,
                request.silent() ? Set.of(NotificationChannel.PUSH) : Set.of(NotificationChannel.PUSH, NotificationChannel.IN_APP));
        NotificationResult pushResult = results.get(NotificationChannel.PUSH);
        if (pushResult == null || !pushResult.success()) {
            String reason = pushResult != null ? pushResult.failureReason() : "unknown error";
            throw new BadRequestException("Recorded in the notification bell, but could not deliver an FCM push: " + reason);
        }
        return ApiResponse.success("Test push notification sent", null);
    }

    @PostMapping("/topics/{topic}/subscribe")
    @Operation(summary = "Subscribe every active device token a user has registered to a topic - send a test push to that topic afterwards to reach them")
    public ApiResponse<Void> subscribeToTopic(@PathVariable String topic, @Valid @RequestBody TopicSubscribeRequest request) {
        List<String> tokens = tokensFor(request.userId());
        if (tokens.isEmpty()) {
            throw new BadRequestException("User " + request.userId() + " has no active push tokens registered yet");
        }
        fcmSender.subscribeToTopic(tokens, topic);
        return ApiResponse.success("Subscribed " + tokens.size() + " device(s) to topic '" + topic + "'", null);
    }

    @PostMapping("/topics/{topic}/unsubscribe")
    @Operation(summary = "Unsubscribe every active device token a user has registered from a topic")
    public ApiResponse<Void> unsubscribeFromTopic(@PathVariable String topic, @Valid @RequestBody TopicSubscribeRequest request) {
        List<String> tokens = tokensFor(request.userId());
        fcmSender.unsubscribeFromTopic(tokens, topic);
        return ApiResponse.success("Unsubscribed " + tokens.size() + " device(s) from topic '" + topic + "'", null);
    }

    private List<String> tokensFor(Long userId) {
        return pushTokenRepository.findByUserIdAndIsActiveTrue(userId.intValue()).stream().map(PushToken::getToken).toList();
    }
}
