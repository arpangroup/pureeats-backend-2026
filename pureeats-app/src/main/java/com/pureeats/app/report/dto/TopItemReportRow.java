package com.pureeats.app.report.dto;

import java.math.BigDecimal;

public record TopItemReportRow(Long itemId, String name, String restaurantName, Integer quantity, BigDecimal revenue) {
}
