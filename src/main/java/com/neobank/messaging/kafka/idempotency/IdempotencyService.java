package com.neobank.messaging.kafka.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service to track processed events and ensure idempotency.
 *
 * Prevents duplicate processing of the same event by multiple consumers.
 * Uses the processed_events table to store which (eventId, consumerName) pairs
 * have been processed.
 *
 * Design Considerations:
 * - Each consumer group can process the same event (different consumer_name)
 * - Within a consumer group, an event is processed only once
 * - Uses database unique constraint for race condition safety
 * - Optimistic: check first, then mark (cheap read before potentially failing write)
 *
 * Thread Safety:
 * - Safe for concurrent callers via unique constraint enforcement
 * - If two threads race to process same event+consumer, one succeeds, one gets constraint violation
 * - Caller should catch DataIntegrityViolationException and treat as "already processed"
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Check if an event has already been processed by a consumer.
     *
     * @param eventId the event ID (from Kafka message)
     * @param consumerName the consumer name (e.g., "analytics-consumer", "reconciliation-consumer")
     * @return true if already processed, false otherwise
     */
    public boolean hasProcessed(UUID eventId, String consumerName) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName);
    }

    /**
     * Mark an event as processed by a consumer.
     *
     * Should be called after successful processing to prevent re-processing on restart.
     * Uses transaction to ensure atomicity.
     *
     * @param eventId the event ID
     * @param consumerName the consumer name
     * @param eventPayload the event payload (for audit trail)
     * @throws org.springframework.dao.DataIntegrityViolationException if already marked as processed
     */
    @Transactional
    public void markProcessed(UUID eventId, String consumerName, String eventPayload) {
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setEventId(eventId);
        entity.setConsumerName(consumerName);
        entity.setEventPayload(eventPayload);
        entity.setProcessedAt(Instant.now());

        processedEventRepository.save(entity);

        log.debug(
                "Event marked as processed: eventId={} consumer={}",
                eventId,
                consumerName
        );
    }

    /**
     * Get the count of processed events (for monitoring).
     *
     * @return count of processed events
     */
    public long getProcessedCount() {
        return processedEventRepository.count();
    }

    /**
     * Clean up old processed events (optional maintenance task).
     *
     * Removes entries older than the specified instant to prevent table growth.
     * Should be called periodically (e.g., weekly).
     *
     * @param olderThan remove entries processed before this instant
     * @return count of deleted records
     */
    @Transactional
    public long cleanupOlderThan(Instant olderThan) {
        long deleted = processedEventRepository.deleteByProcessedAtBefore(olderThan);
        log.info("Cleaned up {} processed event records older than {}", deleted, olderThan);
        return deleted;
    }
}
