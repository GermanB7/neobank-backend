package com.neobank.ledger.api.dto;

import com.neobank.ledger.domain.EntrySide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID ledgerTransactionId,
        UUID accountId,
        EntrySide side,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
}

