package com.pureeats.notification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code audience} is optional and, if present, must match a {@link
 * com.pureeats.notification.enums.PushAudience} constant name (e.g. {@code "CUSTOMER"}) - when set,
 * this token is auto-subscribed to that audience's standing broadcast topic (see
 * {@code PushTokenService#save}), so a promo/announcement can reach "every customer" or "every
 * staff device" with one send, no per-user loop. Omitted or unrecognized means no auto-subscribe -
 * existing callers that don't send it keep today's behavior exactly.
 */
public record SavePushTokenRequest(@NotBlank String token, String audience) {
}
