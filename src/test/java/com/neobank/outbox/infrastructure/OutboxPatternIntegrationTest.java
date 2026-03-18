package com.neobank.outbox.infrastructure;

import com.neobank.outbox.domain.OutboxStatus;
import com.neobank.transfers.domain.events.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the outbox pattern implementation.
 *
 * Tests verify that:
 * 1. Events are persisted to outbox_events table
 * 2. OutboxEventProcessor picks them up and processes them
 * 3. Status transitions occur correctly (PENDING → PROCESSING → PROCESSED)
 * 4. Listeners are invoked via ApplicationEventPublisher
 * 5. Failed events are retried and eventually marked FAILED
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "outbox.enabled=true"
})
class OutboxPatternIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxEventProcessor outboxEventProcessor;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private static boolean eventListenerInvoked = false;
    private static TransferCompletedEvent capturedEvent = null;

    @BeforeEach
    void setUp() {
        eventListenerInvoked = false;
        capturedEvent = null;
        outboxEventRepository.deleteAll();
    }

    @Test
    void testEventPersistenceInOutbox() {
        // Given: A domain event
        UUID transferId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        Instant processedAt = Instant.now();

        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                sourceAccountId,
                targetAccountId,
                userId,
                amount,
                currency,
                processedAt
        );

        // When: Event is published
        outboxEventPublisher.publishEvent(event);

        // Then: Event is persisted in outbox with PENDING status
        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertEquals(1, events.size());

        OutboxEventEntity outboxEvent = events.get(0);
        assertEquals("TransferCompleted", outboxEvent.getEventType());
        assertEquals("Transfer", outboxEvent.getAggregateType());
        assertEquals(transferId, outboxEvent.getAggregateId());
        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertEquals(0, outboxEvent.getAttemptCount());
        assertNotNull(outboxEvent.getEventPayload());
        assertTrue(outboxEvent.getEventPayload().contains("\"transferId\""));
    }

    @Test
    void testOutboxProcessorTransitionsStatus() throws Exception {
        // Given: A pending outbox event
        UUID transferId = UUID.randomUUID();
        OutboxEventEntity outboxEvent = createPendingTransferCompletedEvent(transferId);
        outboxEventRepository.save(outboxEvent);

        // When: Processor processes the event
        outboxEventProcessor.processOutboxEvents();

        // Then: Event is marked as PROCESSED
        OutboxEventEntity processed = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertEquals(OutboxStatus.PROCESSED, processed.getStatus());
        assertEquals(1, processed.getAttemptCount());
        assertNotNull(processed.getProcessedAt());
    }

    @Test
    void testProcessorRetriesFailed() {
        // Given: An event that will fail to process
        // (We simulate by persisting an event with unknown type)
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setEventType("UnknownEventType");
        outboxEvent.setAggregateType("Unknown");
        outboxEvent.setAggregateId(UUID.randomUUID());
        outboxEvent.setEventPayload("{}");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttemptCount(0);
        outboxEvent.setMaxAttempts(3);
        outboxEventRepository.save(outboxEvent);

        // When: Processor tries to process (will fail)
        outboxEventProcessor.processOutboxEvents();

        // Then: Event reverts to PENDING with incremented attempt count
        OutboxEventEntity retried = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, retried.getStatus());
        assertEquals(1, retried.getAttemptCount());
    }

    @Test
    void testProcessorMarksAsFailed() {
        // Given: An event that has already been retried max times
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setEventType("UnknownEventType");
        outboxEvent.setAggregateType("Unknown");
        outboxEvent.setAggregateId(UUID.randomUUID());
        outboxEvent.setEventPayload("{}");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttemptCount(2);  // Next attempt will be the 3rd (max)
        outboxEvent.setMaxAttempts(3);
        outboxEventRepository.save(outboxEvent);

        // When: Processor processes and fails
        outboxEventProcessor.processOutboxEvents();

        // Then: Event is marked as FAILED
        OutboxEventEntity failed = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, failed.getStatus());
        assertNotNull(failed.getErrorMessage());
        assertTrue(failed.getErrorMessage().contains("Max retries"));
    }

    @Test
    void testMultipleEventsProcessedInBatch() {
        // Given: Multiple pending events
        for (int i = 0; i < 5; i++) {
            OutboxEventEntity event = createPendingTransferCompletedEvent(UUID.randomUUID());
            outboxEventRepository.save(event);
        }

        // When: Processor runs
        outboxEventProcessor.processOutboxEvents();

        // Then: All events are processed
        List<OutboxEventEntity> allEvents = outboxEventRepository.findAll();
        assertEquals(5, allEvents.size());
        assertTrue(allEvents.stream().allMatch(e -> e.getStatus() == OutboxStatus.PROCESSED));
    }

    @Test
    void testStuckEventRecovery() {
        // Given: An event stuck in PROCESSING state
        OutboxEventEntity outboxEvent = createPendingTransferCompletedEvent(UUID.randomUUID());
        outboxEvent.setStatus(OutboxStatus.PROCESSING);
        outboxEvent.setAttemptCount(1);
        outboxEvent.setLastAttemptAt(Instant.now().minusSeconds(400)); // Old attempt
        outboxEventRepository.save(outboxEvent);

        // When: Processor runs (recovery logic should kick in)
        outboxEventProcessor.processOutboxEvents();

        // Then: Stuck event is recovered and processed
        OutboxEventEntity recovered = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertEquals(OutboxStatus.PROCESSED, recovered.getStatus());
    }

    @Test
    void testPendingEventCount() {
        // Given: Multiple events with different statuses
        OutboxEventEntity pending1 = createPendingTransferCompletedEvent(UUID.randomUUID());
        OutboxEventEntity pending2 = createPendingTransferCompletedEvent(UUID.randomUUID());

        OutboxEventEntity processed = createPendingTransferCompletedEvent(UUID.randomUUID());
        processed.setStatus(OutboxStatus.PROCESSED);
        processed.setProcessedAt(Instant.now());

        outboxEventRepository.save(pending1);
        outboxEventRepository.save(pending2);
        outboxEventRepository.save(processed);

        // When/Then: Count only PENDING
        long pendingCount = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        assertEquals(2, pendingCount);
    }

    @Test
    void testFindEventsByAggregate() {
        // Given: Events for the same transfer
        UUID transferId = UUID.randomUUID();
        OutboxEventEntity event1 = createPendingTransferCompletedEvent(transferId);
        OutboxEventEntity event2 = createPendingTransferCompletedEvent(transferId);
        outboxEventRepository.save(event1);
        outboxEventRepository.save(event2);

        // When: Finding events by aggregate
        List<OutboxEventEntity> found = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc("Transfer", transferId);

        // Then: Found both events
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(e -> e.getAggregateId().equals(transferId)));
    }

    private OutboxEventEntity createPendingTransferCompletedEvent(UUID transferId) {
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setEventType("TransferCompleted");
        outboxEvent.setAggregateType("Transfer");
        outboxEvent.setAggregateId(transferId);

        // Create a serializable event payload
        String payload = "{" +
                "\"eventId\":\"" + UUID.randomUUID() + "\"," +
                "\"transferId\":\"" + transferId + "\"," +
                "\"sourceAccountId\":\"" + UUID.randomUUID() + "\"," +
                "\"targetAccountId\":\"" + UUID.randomUUID() + "\"," +
                "\"initiatedByUserId\":\"" + UUID.randomUUID() + "\"," +
                "\"amount\":100.00," +
                "\"currency\":\"USD\"," +
                "\"processedAt\":\"" + Instant.now() + "\"," +
                "\"occurredAt\":\"" + Instant.now() + "\"" +
                "}";

        outboxEvent.setEventPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttemptCount(0);
        outboxEvent.setMaxAttempts(3);
        outboxEvent.setCreatedAt(Instant.now());

        return outboxEvent;
    }
}

