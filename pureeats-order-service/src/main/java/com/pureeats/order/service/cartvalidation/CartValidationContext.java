package com.pureeats.order.service.cartvalidation;

import com.pureeats.domain.entity.Item;
import com.pureeats.domain.entity.Restaurant;
import com.pureeats.domain.enums.DeliveryType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * {@code resolvedItems} only contains entries for item IDs that actually exist - a missing entry
 * means the item was deleted/not found.
 * <p>
 * {@code distanceKm}, {@code paymentMode} and {@code userId} are nullable - {@code validate()}
 * (the live Cart-page preview) may not have an address, never has a payment mode yet (chosen later,
 * on Checkout), and always has a caller; {@code assertPlaceable()} (the placeOrder gate) has all
 * three. A rule that needs one of these simply returns no issues when it's null rather than guessing.
 */
public record CartValidationContext(
        Restaurant restaurant,
        List<CartLine> lines,
        Map<Long, Item> resolvedItems,
        DeliveryType deliveryType,
        BigDecimal distanceKm,
        /** Raw {@link com.pureeats.domain.enums.PaymentMode} name, e.g. "COD" - null if not chosen yet. */
        String paymentMode,
        Long userId,
        /** Sum of every resolved line's price*qty+addons, regardless of that line's own availability - see MinimumOrderAmountRule. */
        BigDecimal rawItemTotal
) {
    public Item itemFor(CartLine line) {
        return resolvedItems.get(line.itemId());
    }
}
