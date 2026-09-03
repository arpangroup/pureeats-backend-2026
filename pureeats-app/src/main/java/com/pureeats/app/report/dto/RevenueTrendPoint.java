package com.pureeats.app.report.dto;

import java.math.BigDecimal;

/** {@code date} is ISO yyyy-MM-dd — the admin panel formats it into a display label client-side. */
public record RevenueTrendPoint(String date, BigDecimal revenue, Integer orders) {
}
