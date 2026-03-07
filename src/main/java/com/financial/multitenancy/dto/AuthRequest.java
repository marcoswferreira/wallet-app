package com.financial.multitenancy.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank String tenantId,
        @NotBlank String userId) {
}
