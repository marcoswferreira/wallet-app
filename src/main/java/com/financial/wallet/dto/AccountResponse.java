package com.financial.multitenancy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        BigDecimal balance,
        Integer version,
        Instant createdAt,
        Instant updatedAt) {
}
