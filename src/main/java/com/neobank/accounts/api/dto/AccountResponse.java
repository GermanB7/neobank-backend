package com.neobank.accounts.api.dto;

import com.neobank.accounts.domain.AccountStatus;
import com.neobank.accounts.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        UUID ownerId,
        AccountType type,
        AccountStatus status,
        BigDecimal balance,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
