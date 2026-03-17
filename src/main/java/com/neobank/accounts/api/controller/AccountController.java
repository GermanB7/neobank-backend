package com.neobank.accounts.api.controller;

import com.neobank.accounts.api.dto.AccountResponse;
import com.neobank.accounts.api.dto.AccountSummaryResponse;
import com.neobank.accounts.api.dto.BalanceResponse;
import com.neobank.accounts.api.dto.CreateAccountRequest;
import com.neobank.accounts.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication
    ) {
        try {
            AccountResponse response = accountService.createAccount(request, authentication);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<List<AccountSummaryResponse>> getMyAccounts(Authentication authentication) {
        List<AccountSummaryResponse> response = accountService.getMyAccounts(authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        AccountResponse response = accountService.getAccount(accountId, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        BalanceResponse response = accountService.getBalance(accountId, authentication);
        return ResponseEntity.ok(response);
    }
}

