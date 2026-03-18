package com.neobank.transfers.api.dto;

import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferSummaryResponse(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        TransferKind kind,
        UUID originalTransferId,
        Instant createdAt,
        Instant processedAt
) {
}
