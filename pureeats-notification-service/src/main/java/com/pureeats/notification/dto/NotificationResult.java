package com.pureeats.notification.dto;

public record NotificationResult(boolean success, String providerMessageId, String failureReason) {
    public static NotificationResult success(String providerMessageId) {
        return new NotificationResult(true, providerMessageId, null);
    }

    public static NotificationResult failure(String reason) {
        return new NotificationResult(false, null, reason);
    }
}
