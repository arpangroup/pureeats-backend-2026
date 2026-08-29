package com.pureeats.user.security.metadata;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deliberately a lightweight heuristic, not a full UA database (that would be a new dependency
 * for a "nice to have" - the spec itself warns not to treat User-Agent as a reliable device
 * identifier). Good enough for "which browser/OS is this, roughly" in login history / device UI.
 */
@Component
public class UserAgentParser {

    private static final Pattern BROWSER_PATTERN = Pattern.compile(
            "(Edg|Chrome|Firefox|Safari|OPR)/([\\d.]+)");
    private static final Pattern OS_PATTERN = Pattern.compile(
            "(Windows NT [\\d.]+|Android [\\d.]+|iPhone OS [\\d_]+|Mac OS X [\\d_]+|Linux)");

    public record ParsedUserAgent(String deviceType, String browser, String browserVersion,
                                   String operatingSystem, String osVersion) {
    }

    public ParsedUserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUserAgent("UNKNOWN", null, null, null, null);
        }

        String browser = null;
        String browserVersion = null;
        Matcher browserMatcher = BROWSER_PATTERN.matcher(userAgent);
        if (browserMatcher.find()) {
            browser = normalizeBrowser(browserMatcher.group(1));
            browserVersion = browserMatcher.group(2);
        } else if (userAgent.contains("Safari")) {
            browser = "Safari";
        }

        String os = null;
        String osVersion = null;
        Matcher osMatcher = OS_PATTERN.matcher(userAgent);
        if (osMatcher.find()) {
            String raw = osMatcher.group(1);
            int splitAt = indexOfVersionStart(raw);
            os = splitAt > 0 ? raw.substring(0, splitAt).trim() : raw;
            osVersion = splitAt > 0 ? raw.substring(splitAt).replace('_', '.').trim() : null;
        }

        String deviceType = "DESKTOP";
        if (userAgent.contains("Mobi") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            deviceType = "MOBILE";
        } else if (userAgent.contains("iPad") || userAgent.contains("Tablet")) {
            deviceType = "TABLET";
        }

        return new ParsedUserAgent(deviceType, browser, browserVersion, os, osVersion);
    }

    private String normalizeBrowser(String token) {
        return switch (token) {
            case "Edg" -> "Edge";
            case "OPR" -> "Opera";
            default -> token;
        };
    }

    private int indexOfVersionStart(String raw) {
        Matcher digit = Pattern.compile("[\\d_.]+$").matcher(raw);
        return digit.find() ? digit.start() : -1;
    }
}
