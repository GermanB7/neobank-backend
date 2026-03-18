package com.neobank.transfers.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseTransferRequest(
        @NotBlank @Size(max = 255) String reason
) {
}

