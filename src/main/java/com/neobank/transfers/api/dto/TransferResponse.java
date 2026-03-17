package com.neobank.transfers.api.dto;

import com.neobank.transfers.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String reference,
        UUID initiatedByUserId,
        String idempotencyKey,
        Instant createdAt,
        Instant processedAt
) {
}
