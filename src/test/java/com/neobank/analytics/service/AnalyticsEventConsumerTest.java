package com.neobank.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.analytics.domain.AnalyticsEventEntity;
import com.neobank.analytics.domain.TransferAnalyticsStatus;
import com.neobank.analytics.repository.AnalyticsEventRepository;
import com.neobank.messaging.kafka.idempotency.IdempotencyService;
import com.neobank.messaging.kafka.idempotency.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AnalyticsEventConsumer (Sprint 15).
 *
 * Tests that the consumer correctly processes Kafka events and stores analytics data.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        "neobank.transfer.completed",
        "neobank.transfer.rejected",
        "neobank.transfer.reversed"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "neobank.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.enable-auto-commit=false",
        "spring.kafka.listener.auto-startup=false"
})
class AnalyticsEventConsumerTest {

    @Autowired
    private AnalyticsEventConsumer analyticsConsumer;

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testEventId;
    private UUID testTransferId;

    @BeforeEach
    void setUp() {
        analyticsEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        testEventId = UUID.randomUUID();
        testTransferId = UUID.randomUUID();
    }

    /**
     * Test that a TransferCompleted event is processed correctly.
     */
    @Test
    void testProcessTransferCompletedEvent() {
        // Arrange
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String eventPayload = String.format(
                "{\"eventId\":\"%s\",\"transferId\":\"%s\",\"sourceAccountId\":\"%s\",\"targetAccountId\":\"%s\",\"initiatedByUserId\":\"%s\",\"amount\":\"100.50\",\"currency\":\"USD\",\"processedAt\":\"%s\",\"occurredAt\":\"%s\",\"eventType\":\"TransferCompleted\"}",
                testEventId,
                testTransferId,
                sourceAccountId,
                targetAccountId,
                userId,
                Instant.now(),
                Instant.now()
        );

        // Act
        analyticsConsumer.onTransferCompleted(eventPayload, testEventId.toString());

        // Assert
        var savedEvent = analyticsEventRepository.findByEventId(testEventId);
        assertTrue(savedEvent.isPresent());

        AnalyticsEventEntity event = savedEvent.get();
        assertEquals(testTransferId, event.getTransferId());
        assertEquals(sourceAccountId, event.getSourceAccountId());
        assertEquals(targetAccountId, event.getTargetAccountId());
        assertEquals(new BigDecimal("100.50"), event.getAmount());
        assertEquals("USD", event.getCurrency());
        assertEquals(TransferAnalyticsStatus.COMPLETED, event.getStatus());
        assertEquals(userId, event.getInitiatedByUserId());
    }

    /**
     * Test that a TransferRejected event is processed correctly.
     */
    @Test
    void testProcessTransferRejectedEvent() {
        // Arrange
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String eventPayload = String.format(
                "{\"eventId\":\"%s\",\"transferId\":\"%s\",\"sourceAccountId\":\"%s\",\"targetAccountId\":\"%s\",\"initiatedByUserId\":\"%s\",\"amount\":\"5000.00\",\"currency\":\"EUR\",\"processedAt\":\"%s\",\"occurredAt\":\"%s\",\"eventType\":\"TransferRejectedByRisk\"}",
                testEventId,
                testTransferId,
                sourceAccountId,
                targetAccountId,
                userId,
                Instant.now(),
                Instant.now()
        );

        // Act
        analyticsConsumer.onTransferRejected(eventPayload, testEventId.toString());

        // Assert
        var savedEvent = analyticsEventRepository.findByEventId(testEventId);
        assertTrue(savedEvent.isPresent());

        AnalyticsEventEntity event = savedEvent.get();
        assertEquals(TransferAnalyticsStatus.REJECTED, event.getStatus());
        assertEquals(new BigDecimal("5000.00"), event.getAmount());
        assertEquals("EUR", event.getCurrency());
    }

    /**
     * Test that a TransferReversed event is processed correctly.
     */
    @Test
    void testProcessTransferReversedEvent() {
        // Arrange
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String eventPayload = String.format(
                "{\"eventId\":\"%s\",\"transferId\":\"%s\",\"sourceAccountId\":\"%s\",\"targetAccountId\":\"%s\",\"initiatedByUserId\":\"%s\",\"amount\":\"250.00\",\"currency\":\"USD\",\"processedAt\":\"%s\",\"occurredAt\":\"%s\",\"eventType\":\"TransferReversed\"}",
                testEventId,
                testTransferId,
                sourceAccountId,
                targetAccountId,
                userId,
                Instant.now(),
                Instant.now()
        );

        // Act
        analyticsConsumer.onTransferReversed(eventPayload, testEventId.toString());

        // Assert
        var savedEvent = analyticsEventRepository.findByEventId(testEventId);
        assertTrue(savedEvent.isPresent());

        AnalyticsEventEntity event = savedEvent.get();
        assertEquals(TransferAnalyticsStatus.REVERSED, event.getStatus());
    }

    /**
     * Test idempotency: duplicate events are not reprocessed.
     */
    @Test
    void testDuplicateEventIsNotReprocessed() {
        // Arrange
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String eventPayload = String.format(
                "{\"eventId\":\"%s\",\"transferId\":\"%s\",\"sourceAccountId\":\"%s\",\"targetAccountId\":\"%s\",\"initiatedByUserId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\",\"processedAt\":\"%s\",\"occurredAt\":\"%s\",\"eventType\":\"TransferCompleted\"}",
                testEventId,
                testTransferId,
                sourceAccountId,
                targetAccountId,
                userId,
                Instant.now(),
                Instant.now()
        );

        // Act - process the event twice
        analyticsConsumer.onTransferCompleted(eventPayload, testEventId.toString());
        analyticsConsumer.onTransferCompleted(eventPayload, testEventId.toString());

        // Assert - only one event should be stored (idempotency prevents duplicate)
        long count = analyticsEventRepository.count();
        assertEquals(1, count);
    }

    /**
     * Test that invalid event payload is handled gracefully.
     */
    @Test
    void testInvalidPayloadIsHandledGracefully() {
        // Arrange
        String invalidPayload = "{\"invalid\":\"json\"}";

        // Act & Assert - should not throw, just log error
        assertDoesNotThrow(() ->
                analyticsConsumer.onTransferCompleted(invalidPayload, testEventId.toString())
        );

        // Verify nothing was stored
        assertEquals(0, analyticsEventRepository.count());
    }
}
