package com.pureeats.user.security.metadata;

import com.pureeats.domain.common.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reads the one true source of "who/what is calling" for every auth endpoint. The client
 * (React app) is expected to send a stable {@code X-Device-Id} header; when it doesn't, we
 * derive a best-effort fallback from IP+User-Agent rather than leaving device tracking empty -
 * this is explicitly NOT a strong device fingerprint, just enough to group repeat requests.
 */
@Component
@RequiredArgsConstructor
public class RequestMetadataResolver {

    private static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final UserAgentParser userAgentParser;

    public RequestMetadata resolve(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = fallbackDeviceId(ip, userAgent);
        }

        UserAgentParser.ParsedUserAgent parsed = userAgentParser.parse(userAgent);

        return new RequestMetadata(
                ip,
                userAgent,
                deviceId,
                parsed.deviceType(),
                parsed.browser(),
                parsed.browserVersion(),
                parsed.operatingSystem(),
                parsed.osVersion(),
                RequestIdContext.get()
        );
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String fallbackDeviceId(String ip, String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((ip + "|" + userAgent).getBytes(StandardCharsets.UTF_8));
            return "fallback-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "fallback-unknown";
        }
    }
}
