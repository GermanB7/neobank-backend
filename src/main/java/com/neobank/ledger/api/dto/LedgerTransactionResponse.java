package com.neobank.ledger.api.dto;

import com.neobank.ledger.domain.LedgerTransactionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerTransactionResponse(
        UUID id,
        String reference,
        LedgerTransactionType type,
        UUID relatedTransferId,
        Instant createdAt,
        List<LedgerEntryResponse> entries
) {
}

