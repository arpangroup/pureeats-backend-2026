package com.pureeats.order.service.cartvalidation;

import java.util.List;

/** Loader-agnostic view of one requested cart line — built from either PlaceOrderRequest or CartValidationRequest, so the same rules back both the live "grey out unavailable items" check and the final server-side placeOrder guard. */
public record CartLine(Long itemId, int quantity, List<Long> selectedAddonIds) {
}
