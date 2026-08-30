package com.pureeats.app.dashboard.dto;

import java.math.BigDecimal;

public record TrendPointResponse(String label, int orders, BigDecimal revenue) {
}
