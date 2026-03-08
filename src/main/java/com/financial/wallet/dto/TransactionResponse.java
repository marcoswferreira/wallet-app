package com.financial.wallet.dto;

import com.financial.wallet.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        Instant createdAt) {
}
