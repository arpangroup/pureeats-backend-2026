package com.pureeats.order.dto;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(@NotNull Long riderUserId) {
}
