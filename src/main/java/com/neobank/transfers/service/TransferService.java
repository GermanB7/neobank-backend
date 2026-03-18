package com.neobank.transfers.service;

import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.domain.AccountStatus;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.service.AuditService;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.UserRepository;
import com.neobank.ledger.service.LedgerService;
import com.neobank.risk.domain.RiskDecision;
import com.neobank.risk.domain.RiskEvaluationEntity;
import com.neobank.risk.service.RiskEvaluationService;
import com.neobank.risk.service.RiskPolicyViolationException;
import com.neobank.shared.domain.DomainEventPublisher;
import com.neobank.shared.metrics.ObservabilityMetrics;
import com.neobank.transfers.api.dto.CreateTransferRequest;
import com.neobank.transfers.api.dto.TransferResponse;
import com.neobank.transfers.api.dto.TransferSummaryResponse;
import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;
import com.neobank.transfers.domain.events.TransferCompletedEvent;
import com.neobank.transfers.domain.events.TransferRejectedByRiskEvent;
import com.neobank.transfers.repository.TransferRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final RiskEvaluationService riskEvaluationService;
    private final AuditService auditService;
    private final ObservabilityMetrics observabilityMetrics;
    private final DomainEventPublisher domainEventPublisher;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            UserRepository userRepository,
            LedgerService ledgerService,
            RiskEvaluationService riskEvaluationService,
            AuditService auditService,
            ObservabilityMetrics observabilityMetrics,
            DomainEventPublisher domainEventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.riskEvaluationService = riskEvaluationService;
        this.auditService = auditService;
        this.observabilityMetrics = observabilityMetrics;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional(noRollbackFor = RiskPolicyViolationException.class)
    public TransferResponse createTransfer(CreateTransferRequest request, Authentication authentication) {
        Timer.Sample transferTimer = observabilityMetrics.startTimer();

        UserEntity currentUser = getCurrentUser(authentication);
        UUID initiatedByUserId = currentUser.getId();

        try {
            if (request.sourceAccountId().equals(request.targetAccountId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target accounts must be different");
            }

            BigDecimal normalizedAmount = normalizeAmount(request.amount());
            String normalizedReference = normalizeNullable(request.reference());
            String normalizedIdempotencyKey = normalizeNullable(request.idempotencyKey());

            Optional<TransferEntity> existingIdempotentTransfer = findExistingIdempotentTransfer(
                    initiatedByUserId,
                    normalizedIdempotencyKey,
                    request,
                    normalizedAmount,
                    normalizedReference
            );
            if (existingIdempotentTransfer.isPresent()) {
                return toTransferResponse(existingIdempotentTransfer.get());
            }

            AccountEntity[] orderedAccounts = lockAccountsInDeterministicOrder(request.sourceAccountId(), request.targetAccountId());
            AccountEntity sourceAccount = orderedAccounts[0].getId().equals(request.sourceAccountId())
                    ? orderedAccounts[0]
                    : orderedAccounts[1];
            AccountEntity targetAccount = orderedAccounts[0].getId().equals(request.targetAccountId())
                    ? orderedAccounts[0]
                    : orderedAccounts[1];

            existingIdempotentTransfer = findExistingIdempotentTransfer(
                    initiatedByUserId,
                    normalizedIdempotencyKey,
                    request,
                    normalizedAmount,
                    normalizedReference
            );
            if (existingIdempotentTransfer.isPresent()) {
                return toTransferResponse(existingIdempotentTransfer.get());
            }

            auditService.recordEvent(
                    "TRANSFER_INITIATED",
                    initiatedByUserId,
                    currentUser.getEmail(),
                    "TRANSFER",
                    null,
                    "STARTED",
                    "Transfer requested from source=" + request.sourceAccountId() + " to target=" + request.targetAccountId()
            );

            assertTransferAllowed(sourceAccount, targetAccount, initiatedByUserId, normalizedAmount);

            Timer.Sample riskTimer = observabilityMetrics.startTimer();
            RiskEvaluationEntity evaluation = riskEvaluationService.evaluateAndPersist(
                    sourceAccount,
                    initiatedByUserId,
                    normalizedAmount
            );
            observabilityMetrics.recordRiskEvaluation(riskTimer);

            if (evaluation.getDecision() == RiskDecision.REJECT) {
                observabilityMetrics.incrementTransfersRejected();

                // Publish event for audit and other side effects
                domainEventPublisher.publishEvent(
                        new TransferRejectedByRiskEvent(
                                evaluation.getId(),
                                sourceAccount.getId(),
                                targetAccount.getId(),
                                initiatedByUserId,
                                normalizedAmount,
                                evaluation.getReason()
                        )
                );

                throw new RiskPolicyViolationException(evaluation.getReason());
            }

            TransferEntity transfer = new TransferEntity();
            transfer.setSourceAccountId(sourceAccount.getId());
            transfer.setTargetAccountId(targetAccount.getId());
            transfer.setAmount(normalizedAmount);
            transfer.setCurrency(sourceAccount.getCurrency());
            transfer.setStatus(TransferStatus.PENDING);
            transfer.setKind(TransferKind.STANDARD);
            transfer.setOriginalTransferId(null);
            transfer.setReference(normalizedReference);
            transfer.setInitiatedByUserId(initiatedByUserId);
            transfer.setIdempotencyKey(normalizedIdempotencyKey);

            TransferEntity savedTransfer;
            try {
                savedTransfer = transferRepository.saveAndFlush(transfer);
            } catch (DataIntegrityViolationException ex) {
                if (normalizedIdempotencyKey == null) {
                    throw ex;
                }
                TransferEntity existing = transferRepository
                        .findByInitiatedByUserIdAndIdempotencyKey(initiatedByUserId, normalizedIdempotencyKey)
                        .orElseThrow(() -> ex);
                assertIdempotentRequestMatches(existing, request, normalizedAmount, normalizedReference);
                return toTransferResponse(existing);
            }

            riskEvaluationService.attachTransfer(evaluation.getId(), savedTransfer.getId());

            sourceAccount.setBalance(sourceAccount.getBalance().subtract(normalizedAmount));
            targetAccount.setBalance(targetAccount.getBalance().add(normalizedAmount));
            accountRepository.save(sourceAccount);
            accountRepository.save(targetAccount);

            savedTransfer.setStatus(TransferStatus.COMPLETED);
            savedTransfer.setProcessedAt(Instant.now());
            TransferEntity completed = transferRepository.save(savedTransfer);

            Timer.Sample ledgerTimer = observabilityMetrics.startTimer();
            ledgerService.recordCompletedTransfer(completed);
            observabilityMetrics.recordLedgerRecording(ledgerTimer);

            observabilityMetrics.incrementTransfersCompleted();

            // Publish event for audit and other side effects
            domainEventPublisher.publishEvent(
                    new TransferCompletedEvent(
                            completed.getId(),
                            completed.getSourceAccountId(),
                            completed.getTargetAccountId(),
                            initiatedByUserId,
                            completed.getAmount(),
                            completed.getCurrency(),
                            completed.getProcessedAt()
                    )
            );

            return toTransferResponse(completed);
        } catch (RiskPolicyViolationException ex) {
            log.warn(
                    "Transfer rejected by risk userId={} source={} target={} reason={}",
                    initiatedByUserId,
                    request.sourceAccountId(),
                    request.targetAccountId(),
                    ex.getMessage()
            );
            throw ex;
        } catch (RuntimeException ex) {
            observabilityMetrics.incrementTransfersFailed();
            log.error(
                    "Transfer execution failed userId={} source={} target={} reason={}",
                    initiatedByUserId,
                    request.sourceAccountId(),
                    request.targetAccountId(),
                    ex.getMessage(),
                    ex
            );
            throw ex;
        } finally {
            observabilityMetrics.recordTransferExecution(transferTimer);
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID transferId, Authentication authentication) {
        UserEntity currentUser = getCurrentUser(authentication);

        TransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));

        if (!isAdmin(authentication) && !transfer.getInitiatedByUserId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not allowed to access this transfer");
        }

        return toTransferResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<TransferSummaryResponse> getMyTransfers(Authentication authentication) {
        UUID currentUserId = getCurrentUser(authentication).getId();

        return transferRepository.findByInitiatedByUserIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::toTransferSummaryResponse)
                .toList();
    }

    private AccountEntity[] lockAccountsInDeterministicOrder(UUID sourceAccountId, UUID targetAccountId) {
        UUID firstLockId = sourceAccountId.compareTo(targetAccountId) <= 0 ? sourceAccountId : targetAccountId;
        UUID secondLockId = firstLockId.equals(sourceAccountId) ? targetAccountId : sourceAccountId;

        AccountEntity first = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source or target account not found"));
        AccountEntity second = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source or target account not found"));

        return new AccountEntity[]{first, second};
    }

    private void assertTransferAllowed(
            AccountEntity sourceAccount,
            AccountEntity targetAccount,
            UUID currentUserId,
            BigDecimal amount
    ) {
        if (!sourceAccount.getOwnerId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only initiate transfers from your own accounts");
        }

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE || targetAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both source and target accounts must be ACTIVE");
        }

        if (!sourceAccount.getCurrency().equals(targetAccount.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target account currencies must match");
        }

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount supports up to 2 decimal places");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void assertIdempotentRequestMatches(
            TransferEntity existing,
            CreateTransferRequest request,
            BigDecimal normalizedAmount,
            String normalizedReference
    ) {
        boolean samePayload = existing.getSourceAccountId().equals(request.sourceAccountId())
                && existing.getTargetAccountId().equals(request.targetAccountId())
                && existing.getAmount().compareTo(normalizedAmount) == 0
                && Objects.equals(normalizeNullable(existing.getReference()), normalizedReference);

        if (!samePayload) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key already used with a different transfer payload"
            );
        }
    }

    private Optional<TransferEntity> findExistingIdempotentTransfer(
            UUID initiatedByUserId,
            String normalizedIdempotencyKey,
            CreateTransferRequest request,
            BigDecimal normalizedAmount,
            String normalizedReference
    ) {
        if (normalizedIdempotencyKey == null) {
            return Optional.empty();
        }

        Optional<TransferEntity> existing = transferRepository
                .findByInitiatedByUserIdAndIdempotencyKey(initiatedByUserId, normalizedIdempotencyKey);
        existing.ifPresent(transfer -> assertIdempotentRequestMatches(transfer, request, normalizedAmount, normalizedReference));
        return existing;
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

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private TransferResponse toTransferResponse(TransferEntity transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getTargetAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getKind(),
                transfer.getOriginalTransferId(),
                transfer.getReference(),
                transfer.getInitiatedByUserId(),
                transfer.getIdempotencyKey(),
                transfer.getCreatedAt(),
                transfer.getProcessedAt()
        );
    }

    private TransferSummaryResponse toTransferSummaryResponse(TransferEntity transfer) {
        return new TransferSummaryResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getTargetAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getKind(),
                transfer.getOriginalTransferId(),
                transfer.getCreatedAt(),
                transfer.getProcessedAt()
        );
    }
}

