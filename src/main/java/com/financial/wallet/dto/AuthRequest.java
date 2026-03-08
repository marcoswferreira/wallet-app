package com.financial.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank String tenantId,
        @NotBlank String userId) {
}
