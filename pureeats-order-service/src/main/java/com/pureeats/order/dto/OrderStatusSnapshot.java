package com.pureeats.order.dto;

import java.time.LocalDateTime;

/**
 * The cheap poll target for order tracking — one indexed primary-key lookup plus an in-memory
 * status-code resolution, instead of the several joined lookups (customer, restaurant, live
 * coupon, items+addons, rider assignment+profile) {@link OrderResponse} costs to build. A polling
 * client compares {@code updatedAt} (which changes on every meaningful mutation — status
 * transitions, cancellation, rider assignment) against what it last saw, and only re-fetches the
 * full order when it differs.
 */
public record OrderStatusSnapshot(String status, LocalDateTime updatedAt) {
}
