package com.neobank.reconciliation.service;

import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.service.AuditService;
import com.neobank.ledger.domain.EntrySide;
import com.neobank.ledger.domain.LedgerTransactionEntity;
import com.neobank.ledger.repository.LedgerEntryRepository;
import com.neobank.ledger.repository.LedgerTransactionRepository;
import com.neobank.reconciliation.api.dto.ReconciliationDiscrepancyResponse;
import com.neobank.reconciliation.api.dto.ReconciliationReportResponse;
import com.neobank.reconciliation.domain.DiscrepancyType;
import com.neobank.reconciliation.domain.ReconciliationDiscrepancyEntity;
import com.neobank.reconciliation.domain.ReconciliationReportEntity;
import com.neobank.reconciliation.domain.ReconciliationStatus;
import com.neobank.reconciliation.repository.ReconciliationDiscrepancyRepository;
import com.neobank.reconciliation.repository.ReconciliationReportRepository;
import com.neobank.observability.metrics.ObservabilityMetrics;
import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;
import com.neobank.transfers.repository.TransferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final String FULL_SCOPE = "FULL_SYSTEM";

    private final ReconciliationReportRepository reportRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;
    private final ObservabilityMetrics observabilityMetrics;

    public ReconciliationService(
            ReconciliationReportRepository reportRepository,
            ReconciliationDiscrepancyRepository discrepancyRepository,
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditService auditService,
            ObservabilityMetrics observabilityMetrics
    ) {
        this.reportRepository = reportRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
        this.observabilityMetrics = observabilityMetrics;
    }

    @Transactional
    public ReconciliationReportResponse runFullReconciliation(Authentication authentication) {
        ReconciliationReportEntity report = new ReconciliationReportEntity();
        report.setScope(FULL_SCOPE);
        report.setStatus(ReconciliationStatus.FAILED);
        report.setDiscrepanciesFound(0);
        report = reportRepository.save(report);

        try {
            List<ReconciliationDiscrepancyEntity> discrepancies = new ArrayList<>();
            collectAccountLedgerMismatches(report.getId(), discrepancies);
            collectCompletedTransfersWithoutLedger(report.getId(), discrepancies);
            collectLedgerWithoutValidTransfer(report.getId(), discrepancies);
            collectReversalInconsistencies(report.getId(), discrepancies);

            if (!discrepancies.isEmpty()) {
                discrepancyRepository.saveAll(discrepancies);
            }

            report.setCompletedAt(Instant.now());
            report.setStatus(ReconciliationStatus.COMPLETED);
            report.setDiscrepanciesFound(discrepancies.size());
            ReconciliationReportEntity saved = reportRepository.save(report);

            observabilityMetrics.incrementReconciliationRuns();
            if (!discrepancies.isEmpty()) {
                observabilityMetrics.incrementReconciliationDiscrepancies(discrepancies.size());
            }

            auditService.recordAdminSensitiveAccess(
                    authentication,
                    "RECONCILIATION_REPORT",
                    saved.getId().toString(),
                    "SUCCESS",
                    "Reconciliation run completed with discrepancies=" + discrepancies.size()
            );

            return toReportResponse(saved);
        } catch (RuntimeException ex) {
            report.setCompletedAt(Instant.now());
            report.setStatus(ReconciliationStatus.FAILED);
            reportRepository.save(report);

            auditService.recordAdminSensitiveAccess(
                    authentication,
                    "RECONCILIATION_REPORT",
                    report.getId().toString(),
                    "FAILURE",
                    "Reconciliation failed: " + ex.getMessage()
            );
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ReconciliationReportResponse> listReports() {
        return reportRepository.findAllByOrderByStartedAtDesc()
                .stream()
                .map(this::toReportResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationReportResponse getReport(UUID reportId) {
        return toReportResponse(findReportOrThrow(reportId));
    }

    @Transactional(readOnly = true)
    public List<ReconciliationDiscrepancyResponse> getDiscrepancies(UUID reportId) {
        findReportOrThrow(reportId);

        return discrepancyRepository.findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(this::toDiscrepancyResponse)
                .toList();
    }

    private void collectAccountLedgerMismatches(UUID reportId, List<ReconciliationDiscrepancyEntity> discrepancies) {
        Map<UUID, Map<String, BigDecimal>> ledgerNetByAccountAndCurrency = new HashMap<>();
        for (LedgerEntryRepository.AccountLedgerNetProjection row : ledgerEntryRepository.aggregateNetBalancesByAccount(EntrySide.DEBIT)) {
            ledgerNetByAccountAndCurrency
                    .computeIfAbsent(row.getAccountId(), ignored -> new HashMap<>())
                    .put(row.getCurrency(), normalizeAmount(row.getNetAmount()));
        }

        for (AccountEntity account : accountRepository.findAll()) {
            BigDecimal expectedLedgerBalance = ledgerNetByAccountAndCurrency
                    .getOrDefault(account.getId(), Map.of())
                    .getOrDefault(account.getCurrency(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal actualAccountBalance = normalizeAmount(account.getBalance());

            if (actualAccountBalance.compareTo(expectedLedgerBalance) != 0) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.ACCOUNT_LEDGER_BALANCE_MISMATCH,
                        "ACCOUNT",
                        account.getId().toString(),
                        "Account balance does not match ledger-derived balance",
                        expectedLedgerBalance.toPlainString(),
                        actualAccountBalance.toPlainString()
                ));
            }
        }
    }

    private void collectCompletedTransfersWithoutLedger(UUID reportId, List<ReconciliationDiscrepancyEntity> discrepancies) {
        for (TransferEntity transfer : transferRepository.findByStatusAndKind(TransferStatus.COMPLETED, TransferKind.STANDARD)) {
            if (!ledgerTransactionRepository.existsByRelatedTransferId(transfer.getId())) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.COMPLETED_TRANSFER_WITHOUT_LEDGER,
                        "TRANSFER",
                        transfer.getId().toString(),
                        "Completed transfer does not have an associated ledger transaction",
                        "ledger transaction exists",
                        "missing"
                ));
            }
        }
    }

    private void collectLedgerWithoutValidTransfer(UUID reportId, List<ReconciliationDiscrepancyEntity> discrepancies) {
        List<LedgerTransactionEntity> ledgerTransactions = ledgerTransactionRepository.findAll();
        Set<UUID> transferIds = new HashSet<>();
        for (LedgerTransactionEntity ledgerTransaction : ledgerTransactions) {
            transferIds.add(ledgerTransaction.getRelatedTransferId());
        }

        Map<UUID, TransferEntity> transfersById = new HashMap<>();
        if (!transferIds.isEmpty()) {
            for (TransferEntity transfer : transferRepository.findAllById(transferIds)) {
                transfersById.put(transfer.getId(), transfer);
            }
        }

        for (LedgerTransactionEntity ledgerTransaction : ledgerTransactions) {
            TransferEntity transfer = transfersById.get(ledgerTransaction.getRelatedTransferId());
            if (transfer == null
                    || (transfer.getStatus() != TransferStatus.COMPLETED && transfer.getStatus() != TransferStatus.REVERSED)
                    || !ledgerEntryRepository.existsByLedgerTransactionId(ledgerTransaction.getId())) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.LEDGER_WITHOUT_VALID_TRANSFER,
                        "LEDGER_TRANSACTION",
                        ledgerTransaction.getId().toString(),
                        "Ledger transaction is not linked to a valid terminal transfer",
                        "terminal transfer with ledger entries",
                        transfer == null ? "transfer missing" : "status=" + transfer.getStatus()
                ));
            }
        }
    }

    private void collectReversalInconsistencies(UUID reportId, List<ReconciliationDiscrepancyEntity> discrepancies) {
        for (TransferEntity original : transferRepository.findByStatusAndKind(TransferStatus.REVERSED, TransferKind.STANDARD)) {
            if (!transferRepository.existsByOriginalTransferIdAndKind(original.getId(), TransferKind.REVERSAL)) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.REVERSAL_LINK_INCONSISTENCY,
                        "TRANSFER",
                        original.getId().toString(),
                        "Original transfer is marked REVERSED but no reversal transfer exists",
                        "reversal transfer exists",
                        "missing"
                ));
            }
        }

        List<TransferEntity> reversals = transferRepository.findByKind(TransferKind.REVERSAL);

        Set<UUID> referencedOriginalIds = new HashSet<>();
        for (TransferEntity reversal : reversals) {
            if (reversal.getOriginalTransferId() != null) {
                referencedOriginalIds.add(reversal.getOriginalTransferId());
            }
        }

        Map<UUID, TransferEntity> originalById = new HashMap<>();
        if (!referencedOriginalIds.isEmpty()) {
            for (TransferEntity transfer : transferRepository.findAllById(referencedOriginalIds)) {
                originalById.put(transfer.getId(), transfer);
            }
        }

        for (TransferEntity reversal : reversals) {
            if (reversal.getOriginalTransferId() == null) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.REVERSAL_LINK_INCONSISTENCY,
                        "TRANSFER",
                        reversal.getId().toString(),
                        "Reversal transfer is missing originalTransferId",
                        "originalTransferId set",
                        "null"
                ));
                continue;
            }

            TransferEntity original = originalById.get(reversal.getOriginalTransferId());
            if (original == null || original.getKind() != TransferKind.STANDARD || original.getStatus() != TransferStatus.REVERSED) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.REVERSAL_LINK_INCONSISTENCY,
                        "TRANSFER",
                        reversal.getId().toString(),
                        "Reversal transfer has invalid original transfer linkage",
                        "original transfer with kind=STANDARD and status=REVERSED",
                        original == null
                                ? "original transfer missing"
                                : "kind=" + original.getKind() + ",status=" + original.getStatus()
                ));
            }

            if (reversal.getStatus() == TransferStatus.COMPLETED
                    && !ledgerTransactionRepository.existsByRelatedTransferId(reversal.getId())) {
                discrepancies.add(buildDiscrepancy(
                        reportId,
                        DiscrepancyType.REVERSAL_WITHOUT_LEDGER,
                        "TRANSFER",
                        reversal.getId().toString(),
                        "Completed reversal is missing compensating ledger transaction",
                        "ledger transaction exists",
                        "missing"
                ));
            }
        }

        for (TransferRepository.DuplicateReversalProjection duplicate : transferRepository.findDuplicateReversalOriginals(TransferKind.REVERSAL)) {
            discrepancies.add(buildDiscrepancy(
                    reportId,
                    DiscrepancyType.DUPLICATE_REVERSAL,
                    "TRANSFER",
                    duplicate.getOriginalTransferId().toString(),
                    "Multiple reversal transfers found for a single original transfer",
                    "1",
                    Long.toString(duplicate.getReversalCount())
            ));
        }
    }

    private ReconciliationReportEntity findReportOrThrow(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation report not found"));
    }

    private ReconciliationDiscrepancyEntity buildDiscrepancy(
            UUID reportId,
            DiscrepancyType type,
            String resourceType,
            String resourceId,
            String description,
            String expectedValue,
            String actualValue
    ) {
        ReconciliationDiscrepancyEntity discrepancy = new ReconciliationDiscrepancyEntity();
        discrepancy.setReportId(reportId);
        discrepancy.setType(type);
        discrepancy.setResourceType(resourceType);
        discrepancy.setResourceId(resourceId);
        discrepancy.setDescription(description);
        discrepancy.setExpectedValue(expectedValue);
        discrepancy.setActualValue(actualValue);
        return discrepancy;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private ReconciliationReportResponse toReportResponse(ReconciliationReportEntity report) {
        return new ReconciliationReportResponse(
                report.getId(),
                report.getStartedAt(),
                report.getCompletedAt(),
                report.getScope(),
                report.getStatus(),
                report.getDiscrepanciesFound()
        );
    }

    private ReconciliationDiscrepancyResponse toDiscrepancyResponse(ReconciliationDiscrepancyEntity discrepancy) {
        return new ReconciliationDiscrepancyResponse(
                discrepancy.getId(),
                discrepancy.getReportId(),
                discrepancy.getType(),
                discrepancy.getResourceType(),
                discrepancy.getResourceId(),
                discrepancy.getDescription(),
                discrepancy.getExpectedValue(),
                discrepancy.getActualValue(),
                discrepancy.getCreatedAt()
        );
    }
}

