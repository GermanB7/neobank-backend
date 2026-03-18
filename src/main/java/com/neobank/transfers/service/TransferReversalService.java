package com.neobank.transfers.service;

import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.domain.AccountStatus;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.service.AuditService;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.UserRepository;
import com.neobank.ledger.service.LedgerService;
import com.neobank.shared.domain.DomainEventPublisher;
import com.neobank.transfers.api.dto.ReversalResponse;
import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;
import com.neobank.transfers.domain.events.TransferReversedEvent;
import com.neobank.transfers.repository.TransferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class TransferReversalService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final DomainEventPublisher domainEventPublisher;

    public TransferReversalService(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerService ledgerService,
            AuditService auditService,
            DomainEventPublisher domainEventPublisher
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public ReversalResponse reverseTransfer(UUID transferId, String rawReason, Authentication authentication) {
        UserEntity adminUser = getCurrentUser(authentication);
        assertAdmin(authentication);
        String reason = normalizeRequiredReason(rawReason);

        auditService.recordEvent(
                "TRANSFER_REVERSAL_REQUESTED",
                adminUser.getId(),
                adminUser.getEmail(),
                "TRANSFER",
                transferId.toString(),
                "STARTED",
                "Reversal requested for transferId=" + transferId + " reason=" + reason
        );

        try {
            TransferEntity originalTransfer = transferRepository.findByIdForUpdate(transferId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));

            assertEligibleForReversal(originalTransfer);

            AccountEntity[] orderedAccounts = lockAccountsInDeterministicOrder(
                    originalTransfer.getSourceAccountId(),
                    originalTransfer.getTargetAccountId()
            );
            AccountEntity originalSource = orderedAccounts[0].getId().equals(originalTransfer.getSourceAccountId())
                    ? orderedAccounts[0]
                    : orderedAccounts[1];
            AccountEntity originalTarget = orderedAccounts[0].getId().equals(originalTransfer.getTargetAccountId())
                    ? orderedAccounts[0]
                    : orderedAccounts[1];

            assertReversalAccountsAreEligible(originalTransfer, originalSource, originalTarget);

            TransferEntity reversalTransfer = new TransferEntity();
            reversalTransfer.setSourceAccountId(originalTransfer.getTargetAccountId());
            reversalTransfer.setTargetAccountId(originalTransfer.getSourceAccountId());
            reversalTransfer.setAmount(originalTransfer.getAmount());
            reversalTransfer.setCurrency(originalTransfer.getCurrency());
            reversalTransfer.setStatus(TransferStatus.PENDING);
            reversalTransfer.setKind(TransferKind.REVERSAL);
            reversalTransfer.setOriginalTransferId(originalTransfer.getId());
            reversalTransfer.setReference(buildReversalReference(originalTransfer.getId(), reason));
            reversalTransfer.setInitiatedByUserId(adminUser.getId());
            reversalTransfer.setIdempotencyKey(null);

            TransferEntity savedReversal = transferRepository.saveAndFlush(reversalTransfer);

            originalTarget.setBalance(originalTarget.getBalance().subtract(originalTransfer.getAmount()));
            originalSource.setBalance(originalSource.getBalance().add(originalTransfer.getAmount()));
            accountRepository.save(originalTarget);
            accountRepository.save(originalSource);

            savedReversal.setStatus(TransferStatus.COMPLETED);
            savedReversal.setProcessedAt(Instant.now());
            TransferEntity completedReversal = transferRepository.save(savedReversal);

            ledgerService.recordCompletedTransfer(completedReversal);

            originalTransfer.setStatus(TransferStatus.REVERSED);
            transferRepository.save(originalTransfer);

            domainEventPublisher.publishEvent(
                    new TransferReversedEvent(
                            originalTransfer.getId(),
                            completedReversal.getId(),
                            adminUser.getId(),
                            completedReversal.getSourceAccountId(),
                            completedReversal.getTargetAccountId(),
                            completedReversal.getAmount(),
                            completedReversal.getCurrency(),
                            reason,
                            completedReversal.getProcessedAt()
                    )
            );

            return new ReversalResponse(
                    originalTransfer.getId(),
                    completedReversal.getId(),
                    completedReversal.getSourceAccountId(),
                    completedReversal.getTargetAccountId(),
                    completedReversal.getAmount(),
                    completedReversal.getCurrency(),
                    completedReversal.getKind(),
                    completedReversal.getStatus(),
                    reason,
                    completedReversal.getProcessedAt()
            );
        } catch (RuntimeException ex) {
            auditService.recordEvent(
                    "TRANSFER_REVERSAL_FAILED",
                    adminUser.getId(),
                    adminUser.getEmail(),
                    "TRANSFER",
                    transferId.toString(),
                    "FAILURE",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private void assertEligibleForReversal(TransferEntity originalTransfer) {
        if (originalTransfer.getStatus() != TransferStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only completed transfers can be reversed");
        }

        if (originalTransfer.getKind() == TransferKind.REVERSAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reversal transfers cannot be reversed");
        }

        boolean reversalAlreadyExists = transferRepository.existsByOriginalTransferIdAndKind(
                originalTransfer.getId(),
                TransferKind.REVERSAL
        );
        if (reversalAlreadyExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer has already been reversed");
        }
    }

    private void assertReversalAccountsAreEligible(
            TransferEntity originalTransfer,
            AccountEntity originalSource,
            AccountEntity originalTarget
    ) {
        if (originalSource.getStatus() != AccountStatus.ACTIVE || originalTarget.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both accounts must be ACTIVE to reverse transfer");
        }

        if (!originalSource.getCurrency().equals(originalTarget.getCurrency())
                || !originalSource.getCurrency().equals(originalTransfer.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer/account currencies must match for reversal");
        }

        if (originalTarget.getBalance().compareTo(originalTransfer.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds to execute reversal");
        }
    }

    private AccountEntity[] lockAccountsInDeterministicOrder(UUID firstAccountId, UUID secondAccountId) {
        UUID firstLockId = firstAccountId.compareTo(secondAccountId) <= 0 ? firstAccountId : secondAccountId;
        UUID secondLockId = firstLockId.equals(firstAccountId) ? secondAccountId : firstAccountId;

        AccountEntity first = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source or target account not found"));
        AccountEntity second = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source or target account not found"));

        return new AccountEntity[]{first, second};
    }

    private UserEntity getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void assertAdmin(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin) {
            throw new AccessDeniedException("Only admins can reverse transfers");
        }
    }

    private String normalizeRequiredReason(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reversal reason is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reversal reason is required");
        }
        return normalized;
    }

    private String buildReversalReference(UUID originalTransferId, String reason) {
        String prefix = "reversal:" + originalTransferId + ":";
        String candidate = prefix + reason;
        if (candidate.length() <= 255) {
            return candidate;
        }
        return candidate.substring(0, 255);
    }
}

