package com.neobank.transfers.api.dto;

import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReversalResponse(
        UUID originalTransferId,
        UUID reversalTransferId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currency,
        TransferKind kind,
        TransferStatus status,
        String reason,
        Instant processedAt
) {
}

