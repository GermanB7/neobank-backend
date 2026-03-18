package com.neobank.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.shared.domain.DomainEvent;
import com.neobank.shared.domain.OutboxStatus;
import com.neobank.shared.infrastructure.kafka.KafkaIntegrationEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Processor for durable outbox event publication.
 *
 * This component runs periodically (via OutboxEventScheduler) and processes
 * pending outbox events by publishing them to Spring's ApplicationEventPublisher.
 *
 * Processing Flow:
 * 1. Fetch a batch of PENDING events
 * 2. For each event:
 *    a. Deserialize JSON payload to DomainEvent
 *    b. Mark event as PROCESSING (atomically)
 *    c. Publish to ApplicationEventPublisher (synchronous, in same transaction)
 *    d. On success: mark as PROCESSED
 *    e. On failure: increment attemptCount and revert to PENDING (or FAILED if max retries)
 * 3. Handle stuck events (PROCESSING for too long)
 *
 * Reliability:
 * - PROCESSING status prevents duplicate processing in concurrent scenarios
 * - Retry logic with configurable max attempts
 * - Stuck event recovery (events left in PROCESSING state)
 * - Error tracking and logging for troubleshooting
 *
 * Thread-safety:
 * - Each processor instance processes a batch independently
 * - DB row-level locks protect concurrent processing
 * - PROCESSING status acts as optimistic lock
 */
@Component
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;
    private final Optional<KafkaIntegrationEventDispatcher> kafkaDispatcher;

    @Value("${outbox.processor.batch-size:100}")
    private int batchSize;

    @Value("${outbox.processor.max-attempts:3}")
    private int maxAttempts;

    @Value("${outbox.processor.processing-timeout-ms:300000}")
    private long processingTimeoutMs; // 5 minutes default

    public OutboxEventProcessor(
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            @Autowired(required = false) KafkaIntegrationEventDispatcher kafkaDispatcher
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.kafkaDispatcher = Optional.ofNullable(kafkaDispatcher);
    }

    /**
     * Process pending outbox events in a batch.
     *
     * This method:
     * 1. Fetches PENDING events (up to batchSize)
     * 2. Publishes each to ApplicationEventPublisher
     * 3. Marks successful events as PROCESSED
     * 4. Retries failed events (up to maxAttempts)
     * 5. Marks permanently failed events as FAILED
     *
     * Should be called periodically by OutboxEventScheduler.
     */
    @Transactional
    public void processOutboxEvents() {
        try {
            // Recover stuck events first (those in PROCESSING for too long)
            recoverStuckEvents();

            // Fetch batch of pending events
            Pageable pageable = PageRequest.of(0, batchSize);
            List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAt(
                    OutboxStatus.PENDING,
                    pageable
            );

            if (pendingEvents.isEmpty()) {
                log.debug("No pending outbox events to process");
                return;
            }

            log.info("Processing {} pending outbox events", pendingEvents.size());

            for (OutboxEventEntity outboxEvent : pendingEvents) {
                processEvent(outboxEvent);
            }

            log.info("Batch processing complete, processed {} events", pendingEvents.size());

        } catch (Exception ex) {
            log.error("Unexpected error in outbox processor batch", ex);
        }
    }

    /**
     * Process a single outbox event.
     *
     * @param outboxEvent the event to process
     */
    @Transactional
    void processEvent(OutboxEventEntity outboxEvent) {
        try {
            // Mark as PROCESSING to prevent concurrent processing
            Integer nextAttempt = outboxEvent.getAttemptCount() + 1;
            outboxEventRepository.updateStatus(
                    outboxEvent.getId(),
                    OutboxStatus.PROCESSING,
                    nextAttempt,
                    Instant.now()
            );

            log.debug(
                    "Processing outbox event: id={} type={} attempt={}/{}",
                    outboxEvent.getId(),
                    outboxEvent.getEventType(),
                    nextAttempt,
                    maxAttempts
            );

            // Deserialize and publish event to Spring ApplicationEventPublisher
            DomainEvent domainEvent = deserializeEvent(outboxEvent);
            applicationEventPublisher.publishEvent(domainEvent);

            // Dispatch to Kafka for asynchronous processing (Sprint 15)
            // This is a fire-and-forget operation - failures are logged but don't fail the processor
            if (kafkaDispatcher.isPresent()) {
                try {
                    kafkaDispatcher.get().dispatch(outboxEvent);
                } catch (Exception ex) {
                    log.warn(
                            "Kafka dispatch failed for outbox event: id={} type={} error={}",
                            outboxEvent.getId(),
                            outboxEvent.getEventType(),
                            ex.getMessage()
                    );
                    // Continue - Kafka failure should not prevent marking as PROCESSED
                    // The event is safely in the outbox table for future retry
                }
            }

            // Mark as successfully processed
            outboxEventRepository.markAsProcessed(outboxEvent.getId(), Instant.now());

            log.info(
                    "Outbox event processed successfully: id={} type={} aggregateId={}",
                    outboxEvent.getId(),
                    outboxEvent.getEventType(),
                    outboxEvent.getAggregateId()
            );

        } catch (Exception ex) {
            handleProcessingError(outboxEvent, ex);
        }
    }

    /**
     * Handles errors during event processing.
     *
     * Decides whether to retry or mark as FAILED based on attempt count.
     *
     * @param outboxEvent the event that failed
     * @param ex the exception that occurred
     */
    @Transactional
    void handleProcessingError(OutboxEventEntity outboxEvent, Exception ex) {
        Integer currentAttempt = outboxEvent.getAttemptCount() + 1;
        String errorMessage = ex.getMessage();

        log.warn(
                "Failed to process outbox event: id={} type={} attempt={}/{} error={}",
                outboxEvent.getId(),
                outboxEvent.getEventType(),
                currentAttempt,
                maxAttempts,
                errorMessage,
                ex
        );

        if (currentAttempt < maxAttempts) {
            // Revert to PENDING for retry
            outboxEventRepository.updateStatus(
                    outboxEvent.getId(),
                    OutboxStatus.PENDING,
                    currentAttempt,
                    Instant.now()
            );

            log.info(
                    "Outbox event reverted to PENDING for retry: id={} type={} attempt={}/{}",
                    outboxEvent.getId(),
                    outboxEvent.getEventType(),
                    currentAttempt,
                    maxAttempts
            );

        } else {
            // Max retries exceeded, mark as FAILED
            outboxEventRepository.markAsFailed(
                    outboxEvent.getId(),
                    "Max retries (" + maxAttempts + ") exceeded. Last error: " + errorMessage,
                    Instant.now()
            );

            log.error(
                    "Outbox event marked as FAILED (max retries exceeded): id={} type={} error={}",
                    outboxEvent.getId(),
                    outboxEvent.getEventType(),
                    errorMessage
            );
        }
    }

    /**
     * Recover events stuck in PROCESSING state.
     *
     * If an event has been in PROCESSING for longer than the timeout,
     * it likely means the processor crashed. Revert to PENDING for retry.
     */
    @Transactional
    void recoverStuckEvents() {
        try {
            Instant processingTimeout = Instant.now().minusMillis(processingTimeoutMs);
            List<OutboxEventEntity> stuckEvents = outboxEventRepository.findStuckProcessingEvents(processingTimeout);

            if (stuckEvents.isEmpty()) {
                return;
            }

            log.warn("Found {} stuck events in PROCESSING state, recovering...", stuckEvents.size());

            for (OutboxEventEntity stuckEvent : stuckEvents) {
                // Revert to PENDING without incrementing attempt count
                // (the attempt is already counted)
                outboxEventRepository.updateStatus(
                        stuckEvent.getId(),
                        OutboxStatus.PENDING,
                        stuckEvent.getAttemptCount(),
                        Instant.now()
                );

                log.info(
                        "Recovered stuck event: id={} type={} attempt={}",
                        stuckEvent.getId(),
                        stuckEvent.getEventType(),
                        stuckEvent.getAttemptCount()
                );
            }

        } catch (Exception ex) {
            log.error("Error recovering stuck events", ex);
        }
    }

    /**
     * Deserialize JSON payload to DomainEvent.
     *
     * Uses the event_type field to determine the class to deserialize to.
     * This requires the event class to be resolvable by type name.
     *
     * @param outboxEvent the outbox entity containing serialized event
     * @return deserialized DomainEvent
     * @throws Exception if deserialization fails
     */
    private DomainEvent deserializeEvent(OutboxEventEntity outboxEvent) throws Exception {
        String eventType = outboxEvent.getEventType();
        String payload = outboxEvent.getEventPayload();

        // Map event type to class (simple pattern matching)
        Class<?> eventClass = resolveEventClass(eventType);

        try {
            return (DomainEvent) objectMapper.readValue(payload, eventClass);
        } catch (Exception ex) {
            throw new OutboxEventDeserializationException(
                    "Failed to deserialize event type: " + eventType,
                    ex
            );
        }
    }

    /**
     * Resolve event class by event type name.
     *
     * Maps event type (e.g., "TransferCompleted") to class
     * (e.g., "com.neobank.transfers.domain.events.TransferCompletedEvent").
     *
     * @param eventType the event type string
     * @return the event class
     * @throws ClassNotFoundException if type cannot be resolved
     */
    private Class<?> resolveEventClass(String eventType) throws ClassNotFoundException {
        // Supported event types and their classes
        return switch (eventType) {
            case "TransferCompleted" ->
                    Class.forName("com.neobank.transfers.domain.events.TransferCompletedEvent");
            case "TransferRejectedByRisk" ->
                    Class.forName("com.neobank.transfers.domain.events.TransferRejectedByRiskEvent");
            case "TransferReversed" ->
                    Class.forName("com.neobank.transfers.domain.events.TransferReversedEvent");
            case "AccountCreated" ->
                    Class.forName("com.neobank.accounts.domain.events.AccountCreatedEvent");
            case "UserRegistered" ->
                    Class.forName("com.neobank.auth.domain.events.UserRegisteredEvent");
            default -> throw new ClassNotFoundException("Unknown event type: " + eventType);
        };
    }

    /**
     * Exception for event deserialization failures.
     */
    public static class OutboxEventDeserializationException extends RuntimeException {
        public OutboxEventDeserializationException(String message) {
            super(message);
        }

        public OutboxEventDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

