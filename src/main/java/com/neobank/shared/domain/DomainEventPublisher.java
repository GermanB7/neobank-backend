package com.neobank.shared.domain;

/**
 * Abstraction for publishing domain events.
 *
 * This interface decouples domain code from specific event transport mechanisms
 * (in-process Spring events, message queues, outbox tables, etc.).
 *
 * Current implementation: In-process Spring application events.
 * Future implementations: outbox pattern, Kafka, etc.
 *
 * Contract:
 * - Events are published synchronously within the current transaction
 * - Listeners are invoked synchronously during publishEvent() call
 * - Exceptions from listeners will propagate and may rollback the transaction
 * - Each published event is idempotent (same event object = same result)
 *
 * Design note:
 * We keep this abstraction lightweight. The publisher handles routing to listeners.
 * Listeners decide what to do with each event (audit, notify, trigger workflow, etc.).
 */
public interface DomainEventPublisher {

    /**
     * Publish a domain event to all registered listeners.
     *
     * The event is processed synchronously. Listeners are invoked immediately.
     * If any listener throws an exception, it will propagate.
     *
     * @param event the domain event to publish (never null)
     * @throws IllegalArgumentException if event is null
     */
    void publishEvent(DomainEvent event);
}
