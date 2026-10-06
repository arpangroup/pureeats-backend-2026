package com.pureeats.order.dto;

import java.math.BigDecimal;

/**
 * The platform fee on one order and how it was reached. {@code type} is FLAT or PERCENTAGE;
 * {@code rate} is the ₹ amount (FLAT) or the % (PERCENTAGE); {@code cap} is the PERCENTAGE upper
 * limit (null = none); {@code capped} says whether the cap was what actually applied.
 */
public record PlatformFeeResult(BigDecimal amount, String type, BigDecimal rate, BigDecimal cap, boolean capped) {
    public static final String FLAT = "FLAT";
    public static final String PERCENTAGE = "PERCENTAGE";
}
