package com.pureeats.order.dto;

import java.math.BigDecimal;

public record OrderItemAddonResponse(String addonCategoryName, String addonName, BigDecimal addonPrice) {
}
