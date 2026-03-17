package com.neobank.ledger.service;

import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.service.AuditService;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.UserRepository;
import com.neobank.ledger.api.dto.LedgerEntryResponse;
import com.neobank.ledger.api.dto.LedgerTransactionResponse;
import com.neobank.ledger.domain.EntrySide;
import com.neobank.ledger.domain.LedgerEntryEntity;
import com.neobank.ledger.domain.LedgerTransactionEntity;
import com.neobank.ledger.domain.LedgerTransactionType;
import com.neobank.ledger.repository.LedgerEntryRepository;
import com.neobank.ledger.repository.LedgerTransactionRepository;
import com.neobank.shared.metrics.ObservabilityMetrics;
import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObservabilityMetrics observabilityMetrics;

    public LedgerService(
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObservabilityMetrics observabilityMetrics
    ) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.observabilityMetrics = observabilityMetrics;
    }

    @Transactional
    public LedgerTransactionEntity recordCompletedTransfer(TransferEntity transfer) {
        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new IllegalStateException("Ledger can only be recorded for completed transfers");
        }

        LedgerTransactionEntity transaction = ledgerTransactionRepository.findByRelatedTransferId(transfer.getId())
                .orElseGet(() -> createTransferLedgerTransaction(transfer));

        observabilityMetrics.incrementLedgerRecorded();
        auditService.recordEvent(
                "LEDGER_RECORDED_FOR_TRANSFER",
                transfer.getInitiatedByUserId(),
                null,
                "LEDGER_TRANSACTION",
                transaction.getId().toString(),
                "SUCCESS",
                "Ledger recorded for transferId=" + transfer.getId()
        );

        return transaction;
    }

    @Transactional(readOnly = true)
    public LedgerTransactionResponse getLedgerTransaction(UUID ledgerTransactionId, Authentication authentication) {
        LedgerTransactionEntity transaction = ledgerTransactionRepository.findById(ledgerTransactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger transaction not found"));

        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByLedgerTransactionIdOrderByCreatedAtAsc(transaction.getId());
        assertCanAccessTransaction(entries, authentication);
        return toLedgerTransactionResponse(transaction, entries);
    }

    @Transactional(readOnly = true)
    public LedgerTransactionResponse getLedgerTransactionByTransfer(UUID transferId, Authentication authentication) {
        LedgerTransactionEntity transaction = ledgerTransactionRepository.findByRelatedTransferId(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger transaction not found for transfer"));

        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByLedgerTransactionIdOrderByCreatedAtAsc(transaction.getId());
        assertCanAccessTransaction(entries, authentication);
        return toLedgerTransactionResponse(transaction, entries);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getAccountEntries(UUID accountId, Authentication authentication) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        assertCanAccessAccount(account, authentication);

        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toLedgerEntryResponse)
                .toList();
    }

    private LedgerTransactionEntity createTransferLedgerTransaction(TransferEntity transfer) {
        List<DraftEntry> draftEntries = List.of(
                // Accounting convention for this project:
                // - Outgoing money from an account = CREDIT
                // - Incoming money to an account = DEBIT
                new DraftEntry(transfer.getSourceAccountId(), EntrySide.CREDIT, transfer.getAmount(), transfer.getCurrency()),
                new DraftEntry(transfer.getTargetAccountId(), EntrySide.DEBIT, transfer.getAmount(), transfer.getCurrency())
        );

        validateDraftTransaction(LedgerTransactionType.TRANSFER, transfer.getId(), draftEntries);

        LedgerTransactionEntity transaction = new LedgerTransactionEntity();
        transaction.setReference(buildReference(transfer));
        transaction.setType(LedgerTransactionType.TRANSFER);
        transaction.setRelatedTransferId(transfer.getId());

        LedgerTransactionEntity savedTransaction;
        try {
            savedTransaction = ledgerTransactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            return ledgerTransactionRepository.findByRelatedTransferId(transfer.getId())
                    .orElseThrow(() -> ex);
        }

        List<LedgerEntryEntity> entries = draftEntries.stream()
                .map(draft -> toLedgerEntryEntity(savedTransaction.getId(), draft))
                .toList();
        ledgerEntryRepository.saveAll(entries);

        return savedTransaction;
    }

    private void validateDraftTransaction(
            LedgerTransactionType type,
            UUID relatedTransferId,
            List<DraftEntry> entries
    ) {
        if (type == LedgerTransactionType.TRANSFER && relatedTransferId == null) {
            throw new IllegalArgumentException("Transfer ledger transaction requires related transfer id");
        }

        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("Ledger transaction must have at least two entries");
        }

        String currency = null;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (DraftEntry entry : entries) {
            if (entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Ledger entry amount must be greater than zero");
            }
            if (entry.currency() == null || entry.currency().isBlank()) {
                throw new IllegalArgumentException("Ledger entry currency is required");
            }

            if (currency == null) {
                currency = entry.currency();
            } else if (!currency.equals(entry.currency())) {
                throw new IllegalArgumentException("Ledger transaction must use a single currency");
            }

            if (entry.side() == EntrySide.DEBIT) {
                totalDebit = totalDebit.add(entry.amount());
            } else {
                totalCredit = totalCredit.add(entry.amount());
            }
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("Ledger transaction must be balanced");
        }
    }

    private void assertCanAccessTransaction(List<LedgerEntryEntity> entries, Authentication authentication) {
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger transaction has no entries");
        }

        if (isAdmin(authentication)) {
            return;
        }

        UUID requesterId = getCurrentUser(authentication).getId();

        Set<UUID> accountIds = new LinkedHashSet<>();
        for (LedgerEntryEntity entry : entries) {
            accountIds.add(entry.getAccountId());
        }

        List<AccountEntity> accounts = accountRepository.findAllById(accountIds);
        if (accounts.size() != accountIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger entry account not found");
        }

        for (AccountEntity account : accounts) {
            if (account.getOwnerId().equals(requesterId)) {
                return;
            }
        }

        throw new AccessDeniedException("You are not allowed to access this ledger transaction");
    }

    private void assertCanAccessAccount(AccountEntity account, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }

        UUID requesterId = getCurrentUser(authentication).getId();
        if (!account.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("You are not allowed to access this account ledger entries");
        }
    }

    private UserEntity getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private String buildReference(TransferEntity transfer) {
        if (transfer.getReference() != null && !transfer.getReference().isBlank()) {
            return transfer.getReference().trim();
        }
        return "transfer:" + transfer.getId();
    }

    private LedgerEntryEntity toLedgerEntryEntity(UUID transactionId, DraftEntry draft) {
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setLedgerTransactionId(transactionId);
        entry.setAccountId(draft.accountId());
        entry.setSide(draft.side());
        entry.setAmount(draft.amount());
        entry.setCurrency(draft.currency());
        return entry;
    }

    private LedgerTransactionResponse toLedgerTransactionResponse(
            LedgerTransactionEntity transaction,
            List<LedgerEntryEntity> entries
    ) {
        List<LedgerEntryResponse> entryResponses = new ArrayList<>(entries.size());
        for (LedgerEntryEntity entry : entries) {
            entryResponses.add(toLedgerEntryResponse(entry));
        }

        return new LedgerTransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getType(),
                transaction.getRelatedTransferId(),
                transaction.getCreatedAt(),
                entryResponses
        );
    }

    private LedgerEntryResponse toLedgerEntryResponse(LedgerEntryEntity entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getLedgerTransactionId(),
                entry.getAccountId(),
                entry.getSide(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getCreatedAt()
        );
    }

    private record DraftEntry(UUID accountId, EntrySide side, BigDecimal amount, String currency) {
    }
}

