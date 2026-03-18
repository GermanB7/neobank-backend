package com.neobank.transfers.domain.events;

import com.neobank.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a transfer is rejected by the risk evaluation engine.
 *
 * This event is published AFTER:
 * - Risk evaluation has been performed and marked as REJECT
 * - The exception is about to be thrown to reject the transfer
 *
 * At this point:
 * - NO financial mutation has occurred (balances unchanged)
 * - NO ledger entries have been created
 * - Transfer entity may or may not be persisted (depends on risk flow)
 *
 * This event allows listeners to perform safe side effects like audit logging,
 * risk metric tracking, and future notifications about rejection reasons.
 *
 * Listeners should NOT attempt to mutate financial state in response to this event.
 */
public class TransferRejectedByRiskEvent extends DomainEvent {

    private static final String EVENT_TYPE = "TransferRejectedByRisk";

    private final UUID riskEvaluationId;
    private final UUID sourceAccountId;
    private final UUID targetAccountId;
    private final UUID initiatedByUserId;
    private final BigDecimal amount;
    private final String reason;

    public TransferRejectedByRiskEvent(
            UUID riskEvaluationId,
            UUID sourceAccountId,
            UUID targetAccountId,
            UUID initiatedByUserId,
            BigDecimal amount,
            String reason
    ) {
        super();
        this.riskEvaluationId = riskEvaluationId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.initiatedByUserId = initiatedByUserId;
        this.amount = amount;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    // Accessors
    public UUID getRiskEvaluationId() {
        return riskEvaluationId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public UUID getInitiatedByUserId() {
        return initiatedByUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return super.toString() +
                " TransferRejectedByRiskEvent{" +
                "riskEvaluationId=" + riskEvaluationId +
                ", sourceAccountId=" + sourceAccountId +
                ", amount=" + amount +
                ", reason='" + reason + '\'' +
                '}';
    }
}

