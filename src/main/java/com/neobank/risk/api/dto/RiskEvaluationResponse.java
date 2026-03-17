package com.neobank.risk.api.dto;

import com.neobank.risk.domain.RiskDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RiskEvaluationResponse(
        UUID id,
        UUID transferId,
        UUID sourceAccountId,
        UUID initiatedByUserId,
        BigDecimal amount,
        RiskDecision decision,
        Integer riskScore,
        String triggeredRules,
        String reason,
        Instant createdAt
) {
}

