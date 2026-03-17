package com.neobank.accounts.api.dto;

import com.neobank.accounts.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @NotNull AccountType type,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
        String currency
) {
}

