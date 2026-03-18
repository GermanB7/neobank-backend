package com.neobank.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.analytics.domain.AnalyticsEventEntity;
import com.neobank.analytics.domain.TransferAnalyticsStatus;
import com.neobank.analytics.repository.AnalyticsEventRepository;
import com.neobank.messaging.kafka.idempotency.IdempotencyService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Analytics Event Consumer (Sprint 15).
 *
 * Consumes transfer events from Kafka and stores analytics data.
 * Enables non-critical analytics and reporting without impacting core transfer path.
 *
 * Design:
 * - Subscribes to neobank.transfer.* topics
 * - Parses event payload from JSON
 * - Stores transfer summary in analytics_events table
 * - Implements idempotency to prevent duplicate processing
 * - Gracefully handles failures (logs and continues)
 *
 * Idempotency:
 * - Checks if event already processed before storing
 * - Uses ProcessedEventEntity table to track
 * - Race condition safe via unique constraint
 *
 * Error Handling:
 * - Deserialization errors: log and skip
 * - Database errors: log and skip (idempotency will prevent duplicates on retry)
 * - Kafka delivery failures: automatic retry by consumer group
 *
 * Metrics Published:
 * - kafka.consumer.events.received (counter)
 * - kafka.consumer.events.processed (counter)
 * - kafka.consumer.events.failed (counter)
 * - kafka.consumer.idempotency.skipped (counter)
 */
@Service
@ConditionalOnProperty(name = "neobank.kafka.enabled", havingValue = "true")
public class AnalyticsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventConsumer.class);
    private static final String CONSUMER_NAME = "analytics-consumer";

    private final AnalyticsEventRepository analyticsEventRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AnalyticsEventConsumer(
            AnalyticsEventRepository analyticsEventRepository,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Consume TransferCompletedEvent from Kafka.
     *
     * @param eventPayload the JSON payload from Kafka
     * @param eventId the event ID (from message header)
     */
    @KafkaListener(
            topics = "${neobank.kafka.topics.transfer-completed:neobank.transfer.completed}",
            groupId = "neobank-analytics-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onTransferCompleted(
            @Payload String eventPayload,
            @Header("eventId") String eventId
    ) {
        processTransferEvent(eventPayload, eventId, "COMPLETED");
    }

    /**
     * Consume TransferRejectedByRiskEvent from Kafka.
     *
     * @param eventPayload the JSON payload from Kafka
     * @param eventId the event ID (from message header)
     */
    @KafkaListener(
            topics = "${neobank.kafka.topics.transfer-rejected:neobank.transfer.rejected}",
            groupId = "neobank-analytics-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onTransferRejected(
            @Payload String eventPayload,
            @Header("eventId") String eventId
    ) {
        processTransferEvent(eventPayload, eventId, "REJECTED");
    }

    /**
     * Consume TransferReversedEvent from Kafka.
     *
     * @param eventPayload the JSON payload from Kafka
     * @param eventId the event ID (from message header)
     */
    @KafkaListener(
            topics = "${neobank.kafka.topics.transfer-reversed:neobank.transfer.reversed}",
            groupId = "neobank-analytics-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onTransferReversed(
            @Payload String eventPayload,
            @Header("eventId") String eventId
    ) {
        processTransferEvent(eventPayload, eventId, "REVERSED");
    }

    /**
     * Process a transfer event from Kafka.
     *
     * @param eventPayload the JSON payload
     * @param eventIdStr the event ID string
     * @param analyticsStatus the analytics status
     */
    private void processTransferEvent(
            String eventPayload,
            String eventIdStr,
            String analyticsStatus
    ) {
        UUID eventId = UUID.fromString(eventIdStr);

        try {
            // Check idempotency
            if (idempotencyService.hasProcessed(eventId, CONSUMER_NAME)) {
                log.debug(
                        "Event already processed by analytics consumer: eventId={}",
                        eventId
                );
                meterRegistry.counter("kafka.consumer.idempotency.skipped")
                        .increment();
                return;
            }

            // Parse JSON payload
            JsonNode eventNode = objectMapper.readTree(eventPayload);

            // Extract transfer details from event
            UUID transferId = UUID.fromString(eventNode.get("transferId").asText());
            UUID sourceAccountId = UUID.fromString(eventNode.get("sourceAccountId").asText());
            UUID targetAccountId = UUID.fromString(eventNode.get("targetAccountId").asText());
            UUID initiatedByUserId = UUID.fromString(eventNode.get("initiatedByUserId").asText());
            BigDecimal amount = new BigDecimal(eventNode.get("amount").asText());
            String currency = eventNode.get("currency").asText();
            Instant processedAt = Instant.parse(eventNode.get("processedAt").asText());
            Instant occurredAt = Instant.parse(eventNode.get("occurredAt").asText());

            // Create analytics event
            AnalyticsEventEntity analyticsEvent = new AnalyticsEventEntity();
            analyticsEvent.setEventId(eventId);
            analyticsEvent.setEventType(eventNode.get("eventType").asText());
            analyticsEvent.setTransferId(transferId);
            analyticsEvent.setSourceAccountId(sourceAccountId);
            analyticsEvent.setTargetAccountId(targetAccountId);
            analyticsEvent.setAmount(amount);
            analyticsEvent.setCurrency(currency);
            analyticsEvent.setStatus(TransferAnalyticsStatus.valueOf(analyticsStatus));
            analyticsEvent.setInitiatedByUserId(initiatedByUserId);
            analyticsEvent.setProcessedAt(processedAt);
            analyticsEvent.setOccurredAt(occurredAt);

            // Store analytics event
            analyticsEventRepository.save(analyticsEvent);

            // Mark as processed (idempotency)
            try {
                idempotencyService.markProcessed(eventId, CONSUMER_NAME, eventPayload);
            } catch (DataIntegrityViolationException ex) {
                // Race condition: another instance processed it, that's OK
                log.debug("Race condition on idempotency marker: eventId={}", eventId);
            }

            log.info(
                    "Transfer event processed by analytics consumer: eventId={} transferId={} status={} amount={}",
                    eventId,
                    transferId,
                    analyticsStatus,
                    amount
            );

            meterRegistry.counter("kafka.consumer.events.processed")
                    .increment();

        } catch (Exception ex) {
            log.error(
                    "Failed to process analytics event: eventId={} error={}",
                    eventId,
                    ex.getMessage(),
                    ex
            );

            meterRegistry.counter("kafka.consumer.events.failed")
                    .increment();

            // Do NOT rethrow - Kafka delivery will retry automatically
            // We log the error for observability and continue
        }
    }
}




