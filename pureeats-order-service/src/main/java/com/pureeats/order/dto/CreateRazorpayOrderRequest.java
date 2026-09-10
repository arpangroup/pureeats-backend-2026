package com.pureeats.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** The amount the checkout page is about to charge — the same payable total already shown to the customer there (via the existing cart-pricing flow). This does not need to be tamper-proof on its own: OrderService re-derives the authoritative payable from the cart independently and checks the amount Razorpay actually captured against that before ever persisting the order — see OrderService#placeOrder. */
public record CreateRazorpayOrderRequest(@NotNull @DecimalMin(value = "1.00", message = "Amount must be at least 1.00") BigDecimal amount) {
}
