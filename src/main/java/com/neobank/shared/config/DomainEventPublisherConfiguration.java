package com.neobank.shared.config;

import com.neobank.shared.domain.DomainEventPublisher;
import com.neobank.shared.infrastructure.OutboxEventPublisher;
import com.neobank.shared.infrastructure.SpringDomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for domain event publishing.
 *
 * This configuration sets up the event publishing strategy:
 * - Default (outbox.enabled=true): OutboxEventPublisher (durable outbox pattern)
 * - Fallback (outbox.enabled=false): SpringDomainEventPublisher (in-process events)
 *
 * Only one implementation is active at a time via @Primary.
 */
@Configuration
public class DomainEventPublisherConfiguration {

    /**
     * Outbox-based publisher as the primary implementation.
     *
     * Persists events durably in the outbox_events table for asynchronous
     * processing, solving the dual-write problem.
     *
     * Enabled by default unless explicitly disabled via property.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
    public DomainEventPublisher outboxEventPublisher(OutboxEventPublisher outboxEventPublisher) {
        return outboxEventPublisher;
    }

    /**
     * Spring in-process publisher as fallback.
     *
     * Used for synchronous in-process event publishing when outbox is disabled.
     * Useful for testing or legacy compatibility.
     */
    @Bean
    @ConditionalOnProperty(name = "outbox.enabled", havingValue = "false")
    public DomainEventPublisher springDomainEventPublisher(SpringDomainEventPublisher springDomainEventPublisher) {
        return springDomainEventPublisher;
    }
}

