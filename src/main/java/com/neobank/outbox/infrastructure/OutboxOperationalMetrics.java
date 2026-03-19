package com.neobank.outbox.infrastructure;

import com.neobank.outbox.domain.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Exposes operational outbox gauges for alerting and runbook-driven troubleshooting.
 */
@Component
public class OutboxOperationalMetrics {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxOperationalMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;

        Gauge.builder("neobank.outbox.events.pending", this, OutboxOperationalMetrics::pendingCount)
                .description("Current number of pending outbox events")
                .register(meterRegistry);

        Gauge.builder("neobank.outbox.events.processing", this, OutboxOperationalMetrics::processingCount)
                .description("Current number of outbox events in processing state")
                .register(meterRegistry);

        Gauge.builder("neobank.outbox.events.failed", this, OutboxOperationalMetrics::failedCount)
                .description("Current number of outbox events in failed state")
                .register(meterRegistry);

        Gauge.builder("neobank.outbox.oldest.pending.age.seconds", this, OutboxOperationalMetrics::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest pending outbox event")
                .register(meterRegistry);
    }

    private double pendingCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.PENDING);
    }

    private double processingCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.PROCESSING);
    }

    private double failedCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.FAILED);
    }

    private double oldestPendingAgeSeconds() {
        return outboxEventRepository.findFirstByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
                .map(OutboxEventEntity::getCreatedAt)
                .map(createdAt -> Duration.between(createdAt, Instant.now()).toSeconds())
                .map(Long::doubleValue)
                .orElse(0.0d);
    }
}

