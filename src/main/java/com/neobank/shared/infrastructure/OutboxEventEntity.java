package com.neobank.shared.infrastructure;

import com.neobank.shared.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for outbox events.
 *
 * Represents a domain event persisted durably for asynchronous publishing.
 * Solves the dual-write problem by ensuring that business state changes and
 * event persistence occur atomically in the same transaction.
 *
 * Lifecycle:
 * 1. Created with status=PENDING when business event occurs
 * 2. OutboxProcessor picks it up and marks PROCESSING
 * 3. Event is published to Spring ApplicationEventPublisher
 * 4. Marked as PROCESSED if successful, or FAILED if max retries exceeded
 *
 * The event_payload is stored as JSON for durability and future replay capability.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Type of event, e.g., "TransferCompleted", "TransferRejectedByRisk", etc.
     * Used for routing and deserialization.
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * Type of aggregate root, e.g., "Transfer", "Account", "User".
     * Useful for querying events by business entity type.
     */
    @Column(nullable = false, length = 50)
    private String aggregateType;

    /**
     * ID of the aggregate root (e.g., transfer_id, account_id).
     * Allows tracing all events related to a specific business entity.
     */
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID aggregateId;

    /**
     * Event payload serialized as JSON.
     * Contains all event fields necessary for listeners to process the event.
     * Stored as JSONB for indexing capability in PostgreSQL.
     */
    @Column(nullable = false, columnDefinition = "jsonb")
    private String eventPayload;

    /**
     * Current processing status.
     * Transitions: PENDING → PROCESSING → PROCESSED (or FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    /**
     * Number of processing attempts so far.
     * Incremented each time OutboxProcessor tries to publish the event.
     */
    @Column(nullable = false)
    private Integer attemptCount = 0;

    /**
     * Maximum number of retry attempts before marking as FAILED.
     */
    @Column(nullable = false)
    private Integer maxAttempts = 3;

    /**
     * Last error message if publishing failed.
     * Null if not yet attempted or if successful.
     */
    @Column(columnDefinition = "text")
    private String errorMessage;

    /**
     * Timestamp when the outbox event was created.
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Timestamp when the event was successfully processed.
     * Null until status transitions to PROCESSED.
     */
    @Column
    private Instant processedAt;

    /**
     * Timestamp of the last processing attempt.
     * Helps identify stuck events that are taking too long.
     */
    @Column
    private Instant lastAttemptAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventPayload() {
        return eventPayload;
    }

    public void setEventPayload(String eventPayload) {
        this.eventPayload = eventPayload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    @Override
    public String toString() {
        return "OutboxEventEntity{" +
                "id=" + id +
                ", eventType='" + eventType + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId=" + aggregateId +
                ", status=" + status +
                ", attemptCount=" + attemptCount +
                ", createdAt=" + createdAt +
                ", processedAt=" + processedAt +
                '}';
    }
}

