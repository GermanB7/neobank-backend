package com.neobank.outbox.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task for processing outbox events.
 *
 * This component runs on a fixed interval and invokes the OutboxEventProcessor
 * to handle pending events. Can be disabled via configuration property.
 *
 * Configuration:
 * - outbox.enabled: set to false to completely disable outbox processing (default: true)
 * - outbox.processor.interval: fixed delay in milliseconds (default: 5000)
 *
 * The scheduling is done with a fixed delay (not fixed rate), meaning the interval
 * is measured from the end of one execution to the start of the next. This prevents
 * overlap if processing takes longer than the interval.
 */
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);

    private final OutboxEventProcessor outboxEventProcessor;

    public OutboxEventScheduler(OutboxEventProcessor outboxEventProcessor) {
        this.outboxEventProcessor = outboxEventProcessor;
    }

    /**
     * Process pending outbox events on a fixed schedule.
     *
     * Runs with a fixed delay (not rate), so consecutive executions are spaced
     * by at least the configured interval.
     *
     * Exceptions are logged but do not interrupt the schedule.
     */
    @Scheduled(fixedDelayString = "${outbox.processor.interval:5000}", initialDelayString = "${outbox.processor.initial-delay:1000}")
    public void processOutboxEvents() {
        try {
            log.debug("Starting outbox event processing cycle");
            outboxEventProcessor.processOutboxEvents();
        } catch (Exception ex) {
            log.error("Unexpected error in outbox processing scheduler", ex);
            // Do not rethrow: we want the scheduler to continue running
        }
    }
}

