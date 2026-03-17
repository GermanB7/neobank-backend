package com.neobank.accounts.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        String accountNumber,
        BigDecimal balance,
        String currency
) {
}

