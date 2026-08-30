package com.pureeats.user.security.audit;

import com.pureeats.user.enums.SecurityEventType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable event handed to {@link SecurityEventPublisher} - build via {@link #builder(SecurityEventType)}. */
public record SecurityEvent(
        SecurityEventType eventType,
        Long userId,
        String requestId,
        String ipAddress,
        String userAgent,
        String deviceId,
        String endpoint,
        String httpMethod,
        String result,
        String failureReason,
        Map<String, String> metadata
) {
    public static Builder builder(SecurityEventType eventType) {
        return new Builder(eventType);
    }

    public static final class Builder {
        private final SecurityEventType eventType;
        private Long userId;
        private String requestId;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private String endpoint;
        private String httpMethod;
        private String result = "SUCCESS";
        private String failureReason;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder(SecurityEventType eventType) {
            this.eventType = eventType;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder failure(String reason) {
            this.result = "FAILURE";
            this.failureReason = reason;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public SecurityEvent build() {
            return new SecurityEvent(eventType, userId, requestId, ipAddress, userAgent, deviceId,
                    endpoint, httpMethod, result, failureReason, Map.copyOf(metadata));
        }
    }
}
