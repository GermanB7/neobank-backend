package com.neobank.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events in the Neobank system.
 *
 * Domain events represent meaningful facts that have occurred in the business domain.
 * They capture domain behavior and provide a clean way to decouple core transactional logic
 * from side effects and secondary reactions.
 *
 * Events are published by domain services/aggregates and handled by listeners/subscribers.
 * This abstraction prepares the system for future outbox pattern and Kafka integration.
 *
 * Characteristics:
 * - Immutable and created at a specific point in time
 * - Carry meaningful domain context
 * - Unique event ID for idempotency
 * - Explicit event type for routing and handling
 *
 * Examples:
 * - TransferCompletedEvent: published when a transfer successfully completes
 * - AccountCreatedEvent: published when a new account is created
 * - UserRegisteredEvent: published when a new user registers
 */
public abstract class DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Returns the type of this event for routing, logging, and outbox patterns.
     * Should be a constant domain name like "TransferCompleted" or "AccountCreated".
     *
     * @return the event type identifier
     */
    public abstract String getEventType();

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{eventId=" + eventId +
                ", occurredAt=" + occurredAt +
                ", eventType=" + getEventType() +
                "}";
    }
}
