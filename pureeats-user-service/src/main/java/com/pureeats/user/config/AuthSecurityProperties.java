package com.pureeats.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every configurable knob for the OTP-challenge auth subsystem - see {@code application.yml}
 * under {@code security.*}. Nothing in {@code com.pureeats.user.otp}/{@code .security} hardcodes
 * a length/expiry/limit; they all read this instead.
 */
@ConfigurationProperties(prefix = "security")
public class AuthSecurityProperties {

    private final Otp otp = new Otp();
    private final Session session = new Session();
    private final RateLimit rateLimit = new RateLimit();
    private final Geolocation geolocation = new Geolocation();

    public Otp getOtp() {
        return otp;
    }

    public Session getSession() {
        return session;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Geolocation getGeolocation() {
        return geolocation;
    }

    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 10;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 30;
        private int maxResends = 3;
        private int maxRequestsPerDestinationPerHour = 10;
        private int maxRequestsPerIpPerHour = 20;
        /** How long the login-challenge request waits for the real provider result before responding optimistically and letting delivery finish in the background - see AuthenticationService#awaitPrimaryChannel. A slow SMTP relay no longer means a slow login response. */
        private int sendTimeoutMs = 2000;

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(int expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(int resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }

        public int getMaxResends() {
            return maxResends;
        }

        public void setMaxResends(int maxResends) {
            this.maxResends = maxResends;
        }

        public int getMaxRequestsPerDestinationPerHour() {
            return maxRequestsPerDestinationPerHour;
        }

        public void setMaxRequestsPerDestinationPerHour(int maxRequestsPerDestinationPerHour) {
            this.maxRequestsPerDestinationPerHour = maxRequestsPerDestinationPerHour;
        }

        public int getMaxRequestsPerIpPerHour() {
            return maxRequestsPerIpPerHour;
        }

        public void setMaxRequestsPerIpPerHour(int maxRequestsPerIpPerHour) {
            this.maxRequestsPerIpPerHour = maxRequestsPerIpPerHour;
        }

        public int getSendTimeoutMs() {
            return sendTimeoutMs;
        }

        public void setSendTimeoutMs(int sendTimeoutMs) {
            this.sendTimeoutMs = sendTimeoutMs;
        }
    }

    public static class Session {
        private int accessTokenExpiryMinutes = 15;
        private int refreshTokenExpiryDays = 30;

        public int getAccessTokenExpiryMinutes() {
            return accessTokenExpiryMinutes;
        }

        public void setAccessTokenExpiryMinutes(int accessTokenExpiryMinutes) {
            this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
        }

        public int getRefreshTokenExpiryDays() {
            return refreshTokenExpiryDays;
        }

        public void setRefreshTokenExpiryDays(int refreshTokenExpiryDays) {
            this.refreshTokenExpiryDays = refreshTokenExpiryDays;
        }
    }

    /** One rule reused for every dimension (ip/destination/device) of every endpoint. */
    public static class RateLimitRule {
        private int limit = 10;
        private int windowSeconds = 60;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class RateLimit {
        private final RateLimitRule loginIp = withDefaults(10, 60);
        private final RateLimitRule loginDestination = withDefaults(5, 60);
        private final RateLimitRule verifyIp = withDefaults(20, 60);
        private final RateLimitRule resendIp = withDefaults(10, 60);

        private static RateLimitRule withDefaults(int limit, int windowSeconds) {
            RateLimitRule rule = new RateLimitRule();
            rule.setLimit(limit);
            rule.setWindowSeconds(windowSeconds);
            return rule;
        }

        public RateLimitRule getLoginIp() {
            return loginIp;
        }

        public RateLimitRule getLoginDestination() {
            return loginDestination;
        }

        public RateLimitRule getVerifyIp() {
            return verifyIp;
        }

        public RateLimitRule getResendIp() {
            return resendIp;
        }
    }

    public static class Geolocation {
        private boolean enabled = true;
        private String provider = "ip-api";
        private int timeoutMs = 2000;
        private int cacheTtlMinutes = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getCacheTtlMinutes() {
            return cacheTtlMinutes;
        }

        public void setCacheTtlMinutes(int cacheTtlMinutes) {
            this.cacheTtlMinutes = cacheTtlMinutes;
        }
    }
}
