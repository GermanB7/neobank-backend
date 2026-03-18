package com.neobank.transfers.domain.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.neobank.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a transfer is successfully completed.
 *
 * This event is published AFTER:
 * - Risk evaluation has passed
 * - Source and target account balances have been updated
 * - Transfer status has been set to COMPLETED
 * - Ledger entries have been recorded
 *
 * This event signals that a financial transaction is now complete and can trigger
 * safe side effects like audit recording, notifications, analytics updates, etc.
 *
 * Invariant: If this event is published, the financial mutation has already been
 * committed to the database. Listeners should not need to handle failures of the
 * core transaction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferCompletedEvent extends DomainEvent {

    private static final String EVENT_TYPE = "TransferCompleted";

    private final UUID transferId;
    private final UUID sourceAccountId;
    private final UUID targetAccountId;
    private final UUID initiatedByUserId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant processedAt;

    @JsonCreator
    public TransferCompletedEvent(
            @JsonProperty("transferId") UUID transferId,
            @JsonProperty("sourceAccountId") UUID sourceAccountId,
            @JsonProperty("targetAccountId") UUID targetAccountId,
            @JsonProperty("initiatedByUserId") UUID initiatedByUserId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("processedAt") Instant processedAt
    ) {
        super();
        this.transferId = transferId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.initiatedByUserId = initiatedByUserId;
        this.amount = amount;
        this.currency = currency;
        this.processedAt = processedAt;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    // Accessors
    public UUID getTransferId() {
        return transferId;
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

    public String getCurrency() {
        return currency;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Override
    public String toString() {
        return super.toString() +
                " TransferCompletedEvent{" +
                "transferId=" + transferId +
                ", sourceAccountId=" + sourceAccountId +
                ", targetAccountId=" + targetAccountId +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                '}';
    }
}
