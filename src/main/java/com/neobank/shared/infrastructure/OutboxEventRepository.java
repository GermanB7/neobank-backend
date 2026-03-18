package com.neobank.shared.infrastructure;

import com.neobank.shared.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing OutboxEventEntity from persistent storage.
 *
 * Provides query methods for the OutboxProcessor and admin operations.
 * Supports efficient batch queries for event processing.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Find PENDING events ready to be processed, ordered by creation time.
     * Limited to a batch size to avoid overwhelming the system.
     *
     * @param pageable pagination to limit results
     * @return list of pending outbox events
     */
    List<OutboxEventEntity> findByStatusOrderByCreatedAt(OutboxStatus status, Pageable pageable);

    /**
     * Find events by aggregate type and ID for debugging/admin purposes.
     *
     * @param aggregateType type of aggregate (e.g., "Transfer", "Account")
     * @param aggregateId ID of the aggregate
     * @return list of all events for this aggregate
     */
    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType,
            UUID aggregateId
    );

    /**
     * Find events by type for analytics or retry logic.
     *
     * @param eventType type of event (e.g., "TransferCompleted")
     * @return list of events of this type
     */
    List<OutboxEventEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);

    /**
     * Count PENDING events to monitor system backlog.
     *
     * @return number of pending outbox events
     */
    long countByStatus(OutboxStatus status);

    /**
     * Update status and attempt count in a single transaction.
     * Used by the processor to atomically transition event state.
     *
     * @param id event ID
     * @param newStatus target status
     * @param newAttemptCount new attempt count
     * @param lastAttemptAt timestamp of this attempt
     */
    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = :newStatus, " +
            "e.attemptCount = :newAttemptCount, " +
            "e.lastAttemptAt = :lastAttemptAt " +
            "WHERE e.id = :id")
    void updateStatus(
            @Param("id") UUID id,
            @Param("newStatus") OutboxStatus newStatus,
            @Param("newAttemptCount") Integer newAttemptCount,
            @Param("lastAttemptAt") Instant lastAttemptAt
    );

    /**
     * Mark an event as PROCESSED with success timestamp.
     *
     * @param id event ID
     * @param processedAt successful processing timestamp
     */
    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = 'PROCESSED', " +
            "e.processedAt = :processedAt, " +
            "e.lastAttemptAt = :processedAt " +
            "WHERE e.id = :id")
    void markAsProcessed(@Param("id") UUID id, @Param("processedAt") Instant processedAt);

    /**
     * Mark an event as FAILED with error details.
     *
     * @param id event ID
     * @param errorMessage error description
     * @param failedAt timestamp of failure
     */
    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = 'FAILED', " +
            "e.errorMessage = :errorMessage, " +
            "e.lastAttemptAt = :failedAt " +
            "WHERE e.id = :id")
    void markAsFailed(
            @Param("id") UUID id,
            @Param("errorMessage") String errorMessage,
            @Param("failedAt") Instant failedAt
    );

    /**
     * Find events that have been stuck in PROCESSING for too long.
     * Helps recover from processor crashes or hangs.
     *
     * @param processingTimeout cutoff timestamp (older than this is stuck)
     * @return list of stuck events
     */
    @Query("SELECT e FROM OutboxEventEntity e " +
            "WHERE e.status = 'PROCESSING' " +
            "AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt < :processingTimeout)")
    List<OutboxEventEntity> findStuckProcessingEvents(@Param("processingTimeout") Instant processingTimeout);

    /**
     * Find FAILED events for manual intervention/admin review.
     *
     * @return list of permanently failed events
     */
    List<OutboxEventEntity> findByStatusOrderByLastAttemptAtDesc(OutboxStatus status);
}

