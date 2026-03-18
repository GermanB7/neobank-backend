package com.neobank.shared.infrastructure;

import com.neobank.shared.domain.DomainEvent;
import com.neobank.shared.domain.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-based implementation of DomainEventPublisher.
 *
 * This implementation uses Spring's ApplicationEventPublisher to dispatch domain events
 * to registered listeners within the same process.
 *
 * Benefits:
 * - Leverages Spring's mature event handling infrastructure
 * - Synchronous publication ensures listeners execute within the same transaction
 * - Automatic listener discovery via @EventListener
 * - Clean separation between event publishing and listener concerns
 *
 * Behavior:
 * - Events are published synchronously
 * - Listeners are invoked immediately during publishEvent()
 * - If a listener throws an exception, it propagates to the publisher
 * - Transaction rollback is possible if a listener fails
 *
 * Future evolution:
 * - Could wrap with outbox pattern by persisting events before publishing
 * - Could support async listeners via @Async on listeners
 * - Could integrate with Kafka by wrapping event dispatch
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringDomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishEvent(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        log.debug(
                "Publishing domain event: eventId={}, eventType={}, class={}",
                event.getEventId(),
                event.getEventType(),
                event.getClass().getSimpleName()
        );

        applicationEventPublisher.publishEvent(event);
    }
}

