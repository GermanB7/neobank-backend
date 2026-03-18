package com.neobank.shared.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.shared.domain.OutboxStatus;
import com.neobank.shared.infrastructure.OutboxEventEntity;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka-based Integration Event Dispatcher for Sprint 15.
 *
 * Publishes selected outbox events to Kafka topics for asynchronous processing.
 * Enables non-critical side effects to run outside the main transaction boundary.
 *
 * Design Principles:
 * - Fire-and-forget: publication failures do not fail the business transaction
 * - Non-blocking: publishes asynchronously without waiting for broker confirmation
 * - Event mapping: maps OutboxEventEntity to Kafka messages with proper payload
 * - Topic routing: routes events to appropriate topics based on event type
 * - Metrics: publishes metrics for observability
 * - Logging: logs all operations with event ID and correlation ID
 *
 * Configuration:
 * - neobank.kafka.enabled: global feature flag (default: false)
 * - neobank.kafka.publish-to-kafka: whether to publish (default: false)
 * - neobank.kafka.topics.*: topic name configuration
 *
 * Events Published:
 * - TransferCompleted → neobank.transfer.completed
 * - TransferRejectedByRisk → neobank.transfer.rejected
 * - TransferReversed → neobank.transfer.reversed
 * - AccountCreated → neobank.account.created (optional)
 * - UserRegistered → neobank.user.registered (optional)
 */
@Component
@ConditionalOnProperty(name = "neobank.kafka.enabled", havingValue = "true")
public class KafkaIntegrationEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaIntegrationEventDispatcher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${neobank.kafka.publish-to-kafka:false}")
    private boolean publishToKafka;

    @Value("${neobank.kafka.topics.transfer-completed:neobank.transfer.completed}")
    private String transferCompletedTopic;

    @Value("${neobank.kafka.topics.transfer-rejected:neobank.transfer.rejected}")
    private String transferRejectedTopic;

    @Value("${neobank.kafka.topics.transfer-reversed:neobank.transfer.reversed}")
    private String transferReversedTopic;

    @Value("${neobank.kafka.topics.account-created:neobank.account.created}")
    private String accountCreatedTopic;

    @Value("${neobank.kafka.topics.user-registered:neobank.user.registered}")
    private String userRegisteredTopic;

    public KafkaIntegrationEventDispatcher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Dispatch an outbox event to Kafka if it is publishable.
     *
     * Safely publishes the event asynchronously. Failures are logged but do not
     * propagate to the caller, ensuring the outbox processor continues normally.
     *
     * @param outboxEvent the outbox event entity
     */
    public void dispatch(OutboxEventEntity outboxEvent) {
        if (!publishToKafka) {
            log.debug("Kafka publishing disabled, skipping event id={}", outboxEvent.getId());
            return;
        }

        if (!isPublishable(outboxEvent)) {
            log.debug("Event not publishable to Kafka: type={}", outboxEvent.getEventType());
            return;
        }

        try {
            String topicName = resolveTopicForEventType(outboxEvent.getEventType());
            String eventPayload = outboxEvent.getEventPayload();
            String eventId = outboxEvent.getId().toString();

            // Build Kafka message with event ID as key for partitioning
            Message<String> kafkaMessage = MessageBuilder
                    .withPayload(eventPayload)
                    .setHeader(KafkaHeaders.TOPIC, topicName)
                    .setHeader("kafka_messageKey", eventId)
                    .setHeader("eventId", eventId)
                    .setHeader("eventType", outboxEvent.getEventType())
                    .setHeader("aggregateId", outboxEvent.getAggregateId().toString())
                    .setHeader("aggregateType", outboxEvent.getAggregateType())
                    .build();

            // Send asynchronously with callback for metrics
            kafkaTemplate.send(kafkaMessage).whenComplete((result, ex) -> {
                if (ex != null) {
                    handlePublicationFailure(outboxEvent, (Exception) ex);
                } else {
                    handlePublicationSuccess(outboxEvent, topicName);
                }
            });

        } catch (Exception ex) {
            handlePublicationFailure(outboxEvent, ex);
        }
    }

    /**
     * Check if an event type is publishable to Kafka.
     *
     * Only selected event types are published to avoid unnecessary Kafka traffic.
     *
     * @param outboxEvent the event to check
     * @return true if publishable, false otherwise
     */
    private boolean isPublishable(OutboxEventEntity outboxEvent) {
        return switch (outboxEvent.getEventType()) {
            case "TransferCompleted",
                 "TransferRejectedByRisk",
                 "TransferReversed",
                 "AccountCreated",
                 "UserRegistered" -> true;
            default -> false;
        };
    }

    /**
     * Resolve the Kafka topic name for a given event type.
     *
     * @param eventType the event type
     * @return the topic name
     * @throws IllegalArgumentException if event type is unknown
     */
    private String resolveTopicForEventType(String eventType) {
        return switch (eventType) {
            case "TransferCompleted" -> transferCompletedTopic;
            case "TransferRejectedByRisk" -> transferRejectedTopic;
            case "TransferReversed" -> transferReversedTopic;
            case "AccountCreated" -> accountCreatedTopic;
            case "UserRegistered" -> userRegisteredTopic;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    /**
     * Handle successful publication.
     *
     * Records metrics and logs for observability.
     *
     * @param outboxEvent the event that was published
     * @param topicName the topic it was published to
     */
    private void handlePublicationSuccess(OutboxEventEntity outboxEvent, String topicName) {
        log.info(
                "Event published to Kafka: eventId={} type={} topic={} aggregateId={}",
                outboxEvent.getId(),
                outboxEvent.getEventType(),
                topicName,
                outboxEvent.getAggregateId()
        );

        meterRegistry.counter(
                "outbox.events.published.kafka",
                "eventType", outboxEvent.getEventType(),
                "topic", topicName
        ).increment();
    }

    /**
     * Handle publication failure.
     *
     * Logs the error and records metrics. Does NOT propagate the exception
     * to allow outbox processing to continue.
     *
     * @param outboxEvent the event that failed to publish
     * @param exception the exception that occurred
     */
    private void handlePublicationFailure(OutboxEventEntity outboxEvent, Exception exception) {
        log.warn(
                "Failed to publish event to Kafka: eventId={} type={} reason={}",
                outboxEvent.getId(),
                outboxEvent.getEventType(),
                exception.getMessage(),
                exception
        );

        meterRegistry.counter(
                "outbox.kafka.publish.errors",
                "eventType", outboxEvent.getEventType(),
                "error", exception.getClass().getSimpleName()
        ).increment();

        // Note: We do NOT throw here - Kafka publication failure should not fail
        // the outbox processor. The event remains in outbox for future retry.
    }
}


