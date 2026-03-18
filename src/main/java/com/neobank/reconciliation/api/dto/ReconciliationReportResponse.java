package com.neobank.reconciliation.api.dto;

import com.neobank.reconciliation.domain.ReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationReportResponse(
        UUID id,
        Instant startedAt,
        Instant completedAt,
        String scope,
        ReconciliationStatus status,
        Integer discrepanciesFound
) {
}

