package com.pureeats.app.report.dto;

import java.math.BigDecimal;

public record TopRestaurantReportRow(Long restaurantId, String name, BigDecimal revenue, Integer orders) {
}
