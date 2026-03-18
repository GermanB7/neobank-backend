package com.neobank.risk.service;

import com.neobank.accounts.domain.AccountEntity;
import com.neobank.risk.domain.RiskDecision;
import com.neobank.risk.domain.RiskEvaluationEntity;
import com.neobank.risk.domain.RiskRuleType;
import com.neobank.risk.repository.RiskEvaluationRepository;
import com.neobank.shared.metrics.ObservabilityMetrics;
import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;
import com.neobank.transfers.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RiskEvaluationService {

    private final TransferRepository transferRepository;
    private final RiskEvaluationRepository riskEvaluationRepository;
    private final ObservabilityMetrics observabilityMetrics;

    private final BigDecimal maxTransferAmount;
    private final BigDecimal dailyOutgoingLimit;
    private final int velocityWindowMinutes;
    private final int velocityMaxTransfers;
    private final int newAccountMaxAgeHours;
    private final BigDecimal newAccountHighValueThreshold;

    public RiskEvaluationService(
            TransferRepository transferRepository,
            RiskEvaluationRepository riskEvaluationRepository,
            ObservabilityMetrics observabilityMetrics,
            @Value("${risk.policy.max-transfer-amount:10000.00}") BigDecimal maxTransferAmount,
            @Value("${risk.policy.daily-outgoing-limit:20000.00}") BigDecimal dailyOutgoingLimit,
            @Value("${risk.policy.velocity-window-minutes:10}") int velocityWindowMinutes,
            @Value("${risk.policy.velocity-max-transfers:5}") int velocityMaxTransfers,
            @Value("${risk.policy.new-account-max-age-hours:24}") int newAccountMaxAgeHours,
            @Value("${risk.policy.new-account-high-value-threshold:2500.00}") BigDecimal newAccountHighValueThreshold
    ) {
        this.transferRepository = transferRepository;
        this.riskEvaluationRepository = riskEvaluationRepository;
        this.observabilityMetrics = observabilityMetrics;
        this.maxTransferAmount = maxTransferAmount;
        this.dailyOutgoingLimit = dailyOutgoingLimit;
        this.velocityWindowMinutes = velocityWindowMinutes;
        this.velocityMaxTransfers = velocityMaxTransfers;
        this.newAccountMaxAgeHours = newAccountMaxAgeHours;
        this.newAccountHighValueThreshold = newAccountHighValueThreshold;
    }

    @Transactional
    public RiskEvaluationEntity evaluateAndPersist(
            AccountEntity sourceAccount,
            UUID initiatedByUserId,
            BigDecimal amount
    ) {
        Instant now = Instant.now();
        List<RiskRuleType> triggeredRules = new ArrayList<>();
        int riskScore = 0;

        if (amount.compareTo(maxTransferAmount) > 0) {
            triggeredRules.add(RiskRuleType.AMOUNT_LIMIT);
            riskScore += 70;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant nextDayStart = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        BigDecimal todayOutgoing = transferRepository.sumOutgoingAmountBySourceAndTerminalStatusesAndKindBetween(
                sourceAccount.getId(),
                TransferStatus.COMPLETED,
                TransferStatus.REVERSED,
                TransferKind.STANDARD,
                dayStart,
                nextDayStart
        );
        if (todayOutgoing.add(amount).compareTo(dailyOutgoingLimit) > 0) {
            triggeredRules.add(RiskRuleType.DAILY_LIMIT);
            riskScore += 50;
        }

        Instant windowStart = now.minusSeconds((long) velocityWindowMinutes * 60);
        long transfersInWindow = transferRepository.countBySourceAccountIdAndTerminalStatusesAndKindAndProcessedAtGreaterThanEqual(
                sourceAccount.getId(),
                TransferStatus.COMPLETED,
                TransferStatus.REVERSED,
                TransferKind.STANDARD,
                windowStart
        );
        if (transfersInWindow >= velocityMaxTransfers) {
            triggeredRules.add(RiskRuleType.VELOCITY_LIMIT);
            riskScore += 40;
        }

        Instant newAccountThreshold = now.minusSeconds((long) newAccountMaxAgeHours * 3600);
        if (sourceAccount.getCreatedAt() != null
                && sourceAccount.getCreatedAt().isAfter(newAccountThreshold)
                && amount.compareTo(newAccountHighValueThreshold) > 0) {
            triggeredRules.add(RiskRuleType.NEW_ACCOUNT_HIGH_VALUE);
            riskScore += 35;
        }

        RiskDecision decision = triggeredRules.isEmpty() ? RiskDecision.ALLOW : RiskDecision.REJECT;
        String reasonCode = primaryReasonCode(triggeredRules);

        if (decision == RiskDecision.ALLOW) {
            observabilityMetrics.incrementRiskAllow();
        } else {
            observabilityMetrics.incrementRiskReject();
        }

        RiskEvaluationEntity evaluation = new RiskEvaluationEntity();
        evaluation.setTransferId(null);
        evaluation.setSourceAccountId(sourceAccount.getId());
        evaluation.setInitiatedByUserId(initiatedByUserId);
        evaluation.setAmount(amount);
        evaluation.setDecision(decision);
        evaluation.setRiskScore(riskScore);
        evaluation.setTriggeredRules(joinTriggeredRules(triggeredRules));
        evaluation.setReason(reasonCode);

        return riskEvaluationRepository.save(evaluation);
    }

    @Transactional
    public void attachTransfer(UUID riskEvaluationId, UUID transferId) {
        RiskEvaluationEntity evaluation = riskEvaluationRepository.findById(riskEvaluationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk evaluation not found"));
        evaluation.setTransferId(transferId);
        riskEvaluationRepository.save(evaluation);
    }

    private String joinTriggeredRules(List<RiskRuleType> rules) {
        if (rules.isEmpty()) {
            return "NONE";
        }
        return rules.stream()
                .map(Enum::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("NONE");
    }

    private String primaryReasonCode(List<RiskRuleType> rules) {
        if (rules.isEmpty()) {
            return null;
        }
        RiskRuleType topRule = rules.get(0);
        return switch (topRule) {
            case AMOUNT_LIMIT -> "MAX_TRANSFER_AMOUNT_EXCEEDED";
            case DAILY_LIMIT -> "DAILY_LIMIT_EXCEEDED";
            case VELOCITY_LIMIT -> "VELOCITY_LIMIT_EXCEEDED";
            case NEW_ACCOUNT_HIGH_VALUE -> "NEW_ACCOUNT_HIGH_VALUE_TRANSFER";
        };
    }
}
