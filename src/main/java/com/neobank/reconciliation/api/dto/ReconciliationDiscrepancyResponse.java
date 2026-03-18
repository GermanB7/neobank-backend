package com.neobank.reconciliation.api.dto;

import com.neobank.reconciliation.domain.DiscrepancyType;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationDiscrepancyResponse(
        UUID id,
        UUID reportId,
        DiscrepancyType type,
        String resourceType,
        String resourceId,
        String description,
        String expectedValue,
        String actualValue,
        Instant createdAt
) {
}

