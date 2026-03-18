package com.neobank.transfers.domain.events;

import com.neobank.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransferReversedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "TransferReversed";

    private final UUID originalTransferId;
    private final UUID reversalTransferId;
    private final UUID initiatedByUserId;
    private final UUID sourceAccountId;
    private final UUID targetAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final String reason;
    private final Instant processedAt;

    public TransferReversedEvent(
            UUID originalTransferId,
            UUID reversalTransferId,
            UUID initiatedByUserId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount,
            String currency,
            String reason,
            Instant processedAt
    ) {
        this.originalTransferId = originalTransferId;
        this.reversalTransferId = reversalTransferId;
        this.initiatedByUserId = initiatedByUserId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.processedAt = processedAt;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    public UUID getOriginalTransferId() {
        return originalTransferId;
    }

    public UUID getReversalTransferId() {
        return reversalTransferId;
    }

    public UUID getInitiatedByUserId() {
        return initiatedByUserId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

