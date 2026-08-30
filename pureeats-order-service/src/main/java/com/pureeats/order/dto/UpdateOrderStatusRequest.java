package com.pureeats.order.dto;

import com.pureeats.domain.enums.OrderStatusCode;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatusCode toStatus) {
}
