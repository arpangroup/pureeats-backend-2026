package com.pureeats.order.dto;

import java.util.List;

public record CartValidationResponse(
        CartRestaurantValidationResponse restaurant,
        List<CartItemValidationResponse> items,
        /** Null when the request carried no couponCode. */
        CartCouponValidationResponse coupon,
        CartPricingResponse pricing,
        /** True if the restaurant itself, or any individual item, is unavailable - the frontend gates "proceed to pay" on this. */
        boolean anyUnavailable
) {
}
