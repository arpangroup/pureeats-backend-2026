package com.pureeats.app.dashboard.dto;

import java.math.BigDecimal;

public record TopRestaurantResponse(String name, BigDecimal revenue, long orders) {
}
