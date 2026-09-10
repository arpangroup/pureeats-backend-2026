package com.pureeats.order.service.payment;

public record RazorpayOrderInfo(String razorpayOrderId, long amountPaise, String currency, String keyId) {
}
