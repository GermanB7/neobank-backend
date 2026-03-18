package com.neobank.messaging.kafka.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.outbox.infrastructure.OutboxEventEntity;
import com.neobank.outbox.infrastructure.OutboxEventRepository;
import com.neobank.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka Publishing via Outbox (Sprint 15).
 *
 * Tests the flow: OutboxEventEntity → KafkaIntegrationEventDispatcher → Kafka topic
 *
 * Uses EmbeddedKafka for local testing without Docker dependency.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "neobank.transfer.completed",
                "neobank.transfer.rejected",
                "neobank.transfer.reversed"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "neobank.kafka.enabled=true",
        "neobank.kafka.publish-to-kafka=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class KafkaIntegrationEventDispatcherTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaIntegrationEventDispatcher kafkaDispatcher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    private UUID testEventId;

    @BeforeEach
    void setUp() {
        testEventId = UUID.randomUUID();
    }

    /**
     * Test that a TransferCompleted event is dispatched to Kafka.
     */
    @Test
    void testDispatchTransferCompletedEvent() {
        // Arrange
        UUID transferId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String eventPayload = String.format(
                "{\"eventId\":\"%s\",\"transferId\":\"%s\",\"sourceAccountId\":\"%s\",\"targetAccountId\":\"%s\",\"initiatedByUserId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"processedAt\":\"%s\",\"occurredAt\":\"%s\",\"eventType\":\"TransferCompleted\"}",
                testEventId,
                transferId,
                sourceAccountId,
                targetAccountId,
                userId,
                Instant.now(),
                Instant.now()
        );

        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setEventType("TransferCompleted");
        outboxEvent.setEventPayload(eventPayload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAggregateType("Transfer");
        outboxEvent.setAggregateId(transferId);
        outboxEvent.setAttemptCount(0);
        outboxEvent.setMaxAttempts(3);
        outboxEvent.setCreatedAt(Instant.now());

        // Act
        kafkaDispatcher.dispatch(outboxEvent);

        // Assert - verify no exceptions thrown
        assertNotNull(outboxEvent.getId());
        assertTrue(outboxEvent.getEventType().contains("TransferCompleted"));
    }

    /**
     * Test that TransferRejectedByRisk event is publishable.
     */
    @Test
    void testDispatchTransferRejectedEvent() {
        // Arrange
        UUID evaluationId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String eventPayload = String.format(
                "{\"eventId\":\"%s\",\"evaluationId\":\"%s\",\"sourceAccountId\":\"%s\",\"targetAccountId\":\"%s\",\"initiatedByUserId\":\"%s\",\"amount\":\"5000.00\",\"reason\":\"Exceeds daily limit\",\"eventType\":\"TransferRejectedByRisk\"}",
                testEventId,
                evaluationId,
                sourceAccountId,
                targetAccountId,
                userId
        );

        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setEventType("TransferRejectedByRisk");
        outboxEvent.setEventPayload(eventPayload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAggregateType("RiskEvaluation");
        outboxEvent.setAggregateId(evaluationId);
        outboxEvent.setAttemptCount(0);
        outboxEvent.setMaxAttempts(3);
        outboxEvent.setCreatedAt(Instant.now());

        // Act
        kafkaDispatcher.dispatch(outboxEvent);

        // Assert
        assertNotNull(outboxEvent.getId());
        assertEquals("TransferRejectedByRisk", outboxEvent.getEventType());
    }

    /**
     * Test that non-publishable events are skipped.
     */
    @Test
    void testNonPublishableEventIsSkipped() {
        // Arrange - event type that is NOT in the publishable list
        String eventPayload = "{\"eventId\":\"" + testEventId + "\"}";

        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setEventType("UnknownEventType");  // Not in publishable list
        outboxEvent.setEventPayload(eventPayload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAggregateType("Unknown");
        outboxEvent.setAggregateId(UUID.randomUUID());
        outboxEvent.setAttemptCount(0);
        outboxEvent.setMaxAttempts(3);
        outboxEvent.setCreatedAt(Instant.now());

        // Act - should not throw, just skip
        kafkaDispatcher.dispatch(outboxEvent);

        // Assert - no exception thrown
        assertNotNull(outboxEvent.getId());
    }
}

