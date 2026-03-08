package com.financial.multitenancy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
        @NotNull(message = "userId is required") UUID userId,

        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative") BigDecimal initialBalance) {
}
