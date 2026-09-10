package com.pureeats.app.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/v1/admin/notifications/test/email - see AdminNotificationTestController. */
public record TestEmailRequest(@NotBlank @Email String to) {
}
