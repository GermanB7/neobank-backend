package com.neobank.accounts.service;

import com.neobank.accounts.api.dto.AccountResponse;
import com.neobank.accounts.api.dto.AccountSummaryResponse;
import com.neobank.accounts.api.dto.BalanceResponse;
import com.neobank.accounts.api.dto.CreateAccountRequest;
import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.domain.AccountStatus;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountService {

    private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "EUR");
    private static final String DEFAULT_CURRENCY = "USD";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, Authentication authentication) {
        UUID ownerId = getCurrentUser(authentication).getId();

        AccountEntity account = new AccountEntity();
        account.setAccountNumber(generateAccountNumber());
        account.setOwnerId(ownerId);
        account.setType(request.type());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY));
        account.setCurrency(normalizeCurrency(request.currency()));

        AccountEntity saved = accountRepository.save(account);
        return toAccountResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> getMyAccounts(Authentication authentication) {
        UUID ownerId = getCurrentUser(authentication).getId();

        return accountRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(this::toAccountSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId, Authentication authentication) {
        AccountEntity account = findAccountById(accountId);
        assertCanAccess(account, authentication);
        return toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId, Authentication authentication) {
        AccountEntity account = findAccountById(accountId);
        assertCanAccess(account, authentication);

        return new BalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency()
        );
    }

    private AccountEntity findAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private void assertCanAccess(AccountEntity account, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }

        UUID requesterId = getCurrentUser(authentication).getId();
        if (!account.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("You are not allowed to access this account");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private UserEntity getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < 3; attempt++) {
            long sequenceValue = accountRepository.nextAccountNumberSequenceValue();
            String candidate = "ACC" + String.format("%010d", sequenceValue);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique account number");
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }

        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CURRENCIES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported currency. Allowed values: " + ALLOWED_CURRENCIES);
        }
        return normalized;
    }

    private AccountResponse toAccountResponse(AccountEntity account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwnerId(),
                account.getType(),
                account.getStatus(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private AccountSummaryResponse toAccountSummaryResponse(AccountEntity account) {
        return new AccountSummaryResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwnerId(),
                account.getType(),
                account.getStatus(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt()
        );
    }
}

