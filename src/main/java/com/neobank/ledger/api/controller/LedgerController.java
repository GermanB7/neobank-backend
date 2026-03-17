package com.neobank.ledger.api.controller;

import com.neobank.ledger.api.dto.LedgerEntryResponse;
import com.neobank.ledger.api.dto.LedgerTransactionResponse;
import com.neobank.ledger.service.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/transactions/{ledgerTransactionId}")
    public ResponseEntity<LedgerTransactionResponse> getLedgerTransaction(
            @PathVariable UUID ledgerTransactionId,
            Authentication authentication
    ) {
        LedgerTransactionResponse response = ledgerService.getLedgerTransaction(ledgerTransactionId, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/transfer/{transferId}")
    public ResponseEntity<LedgerTransactionResponse> getLedgerTransactionByTransfer(
            @PathVariable UUID transferId,
            Authentication authentication
    ) {
        LedgerTransactionResponse response = ledgerService.getLedgerTransactionByTransfer(transferId, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts/{accountId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getAccountLedgerEntries(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        List<LedgerEntryResponse> response = ledgerService.getAccountEntries(accountId, authentication);
        return ResponseEntity.ok(response);
    }
}

