package com.pureeats.order.dto;

/** Everything the customer app needs to open Razorpay Checkout — keyId is the public Key ID (safe client-side), never the secret. */
public record RazorpayOrderResponse(String razorpayOrderId, String keyId, long amountPaise, String currency) {
}
