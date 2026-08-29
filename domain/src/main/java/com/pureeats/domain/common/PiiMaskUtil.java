package com.pureeats.domain.common;

/**
 * Pure, framework-free masking for PII that ends up in API responses, notification logs and
 * audit records - e.g. {@code j***n@gmail.com}, {@code ******3210}. Never throws on odd input;
 * worst case it returns the input unmasked-but-short rather than leaking a stack trace.
 */
public final class PiiMaskUtil {

    private PiiMaskUtil() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return mask(email, 1, 0);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String maskedLocal = local.length() <= 2
                ? local.charAt(0) + "*".repeat(Math.max(1, local.length() - 1))
                : local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1);
        return maskedLocal + domain;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        if (phone.length() <= 4) {
            return "*".repeat(phone.length());
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    /** Masks everything except {@code keepStart} leading and {@code keepEnd} trailing characters. */
    public static String mask(String value, int keepStart, int keepEnd) {
        if (value == null) {
            return null;
        }
        int len = value.length();
        if (len <= keepStart + keepEnd) {
            return "*".repeat(len);
        }
        return value.substring(0, keepStart) + "*".repeat(len - keepStart - keepEnd) + value.substring(len - keepEnd);
    }

    /** Masks either an email or a phone number, auto-detected by the presence of '@'. */
    public static String maskDestination(String destination) {
        if (destination == null) {
            return null;
        }
        return destination.contains("@") ? maskEmail(destination) : maskPhone(destination);
    }
}
