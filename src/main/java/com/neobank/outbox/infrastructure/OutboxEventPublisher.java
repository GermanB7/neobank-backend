package com.neobank.outbox.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.shared.domain.DomainEvent;
import com.neobank.shared.domain.DomainEventPublisher;
import com.neobank.outbox.domain.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;



/**
 * Outbox-based implementation of DomainEventPublisher.
 *
 * This implementation persists domain events durably in the outbox_events table
 * before returning from publishEvent(). This solves the dual-write problem by
 * ensuring that business state changes and event persistence occur atomically.
 *
 * The event is stored as JSON payload and will be processed asynchronously by
 * the OutboxEventProcessor, which runs on a scheduled interval.
 *
 * Transactional Contract:
 * - If the business transaction commits, the outbox event also commits
 * - If the business transaction rolls back, the outbox event is not persisted
 * - This provides a "fire and forget" guarantee: if publishEvent() returns,
 *   the event will eventually be processed (barring infrastructure failure)
 *
 * Thread-safety:
 * - Thread-safe via Spring transaction management
 * - Concurrent transactions are isolated by DB (MVCC in PostgreSQL)
 * - Outbox processing handles concurrent processing attempts gracefully
 */
public class OutboxEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a domain event by persisting it in the outbox table.
     *
     * The event is stored with status=PENDING and will be picked up by the
     * OutboxEventProcessor for asynchronous publishing to listeners.
     *
     * @param event the domain event to publish (never null)
     * @throws IllegalArgumentException if event is null
     * @throws OutboxPersistenceException if serialization or DB persistence fails
     */
    @Override
    @Transactional
    public void publishEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        try {
            // Extract event metadata and payload
            String eventType = event.getEventType();
            UUID eventId = event.getEventId();

            // Serialize event to JSON
            String eventPayload = objectMapper.writeValueAsString(event);

            // Determine aggregate information from event
            String aggregateType = extractAggregateType(event);
            UUID aggregateId = extractAggregateId(event);

            // Create outbox entity
            OutboxEventEntity outboxEvent = new OutboxEventEntity();
            outboxEvent.setEventType(eventType);
            outboxEvent.setEventPayload(eventPayload);
            outboxEvent.setStatus(OutboxStatus.PENDING);
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setAttemptCount(0);
            outboxEvent.setMaxAttempts(3);
            outboxEvent.setCreatedAt(Instant.now());

            // Persist in outbox
            OutboxEventEntity persisted = outboxEventRepository.save(outboxEvent);

            log.debug(
                    "Outbox event persisted: id={} type={} aggregate={}:{}",
                    persisted.getId(),
                    eventType,
                    aggregateType,
                    aggregateId
            );

        } catch (Exception ex) {
            log.error("Failed to persist outbox event: type={}", event.getEventType(), ex);
            throw new OutboxPersistenceException("Failed to persist outbox event", ex);
        }
    }

    /**
     * Extracts the aggregate type from the event.
     *
     * Uses reflection and naming conventions:
     * - TransferCompletedEvent → "Transfer"
     * - AccountCreatedEvent → "Account"
     * - UserRegisteredEvent → "User"
     *
     * Can be overridden or extended for custom types.
     *
     * @param event the domain event
     * @return aggregate type string
     */
    private String extractAggregateType(DomainEvent event) {
        String className = event.getClass().getSimpleName();

        if (className.endsWith("Event")) {
            className = className.substring(0, className.length() - 5);
        }

        String[] actionSuffixes = {
                "RejectedByRisk",
                "Completed",
                "Reversed",
                "Registered",
                "Created"
        };

        for (String suffix : actionSuffixes) {
            if (className.endsWith(suffix) && className.length() > suffix.length()) {
                return className.substring(0, className.length() - suffix.length());
            }
        }

        return className;
    }

    /**
     * Extracts the aggregate ID from the event using reflection.
     *
     * Looks for methods following naming conventions:
     * - getTransferId() → transferId
     * - getAccountId() → accountId
     * - getUserId() → userId
     * - getAggregateId() → aggregateId
     *
     * Falls back to event ID if no aggregate ID is found.
     *
     * @param event the domain event
     * @return aggregate ID UUID
     */
    private UUID extractAggregateId(DomainEvent event) {
        // Try common patterns
        String[] patterns = {
                "getTransferId",
                "getAccountId",
                "getUserId",
                "getAggregateId",
                "getId"
        };

        for (String methodName : patterns) {
            try {
                var method = event.getClass().getMethod(methodName);
                Object result = method.invoke(event);
                if (result instanceof UUID) {
                    return (UUID) result;
                }
            } catch (Exception ignored) {
                // Continue to next pattern
            }
        }

        // Fallback to event ID as aggregate ID
        log.warn(
                "Could not extract aggregate ID from event {}, using event ID",
                event.getClass().getSimpleName()
        );
        return event.getEventId();
    }

    /**
     * Exception thrown when outbox event persistence fails.
     */
    public static class OutboxPersistenceException extends RuntimeException {
        public OutboxPersistenceException(String message) {
            super(message);
        }

        public OutboxPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}






