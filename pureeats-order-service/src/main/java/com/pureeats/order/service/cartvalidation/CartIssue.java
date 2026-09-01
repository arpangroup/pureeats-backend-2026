package com.pureeats.order.service.cartvalidation;

/** One problem found by a {@link CartValidationRule}. {@code itemId} null means the issue applies to the whole cart (e.g. the restaurant itself), not one line. */
public record CartIssue(Long itemId, String reason) {
    public static CartIssue restaurantLevel(String reason) {
        return new CartIssue(null, reason);
    }
}
