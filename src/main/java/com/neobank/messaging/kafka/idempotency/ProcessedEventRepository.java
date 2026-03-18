package com.neobank.messaging.kafka.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for ProcessedEventEntity.
 *
 * Provides idempotency tracking for Kafka consumers.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    /**
     * Check if an event has been processed by a specific consumer.
     *
     * @param eventId the event ID
     * @param consumerName the consumer name
     * @return true if exists, false otherwise
     */
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);

    /**
     * Delete processed event records older than a given timestamp.
     *
     * Used for maintenance to prevent table unbounded growth.
     *
     * @param olderThan delete entries processed before this time
     * @return count of deleted records
     */
    long deleteByProcessedAtBefore(Instant olderThan);
}
