package com.neobank.shared.infrastructure.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdempotencyService (Sprint 15).
 *
 * Tests idempotency tracking and race condition handling.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private UUID testEventId;
    private String testConsumerName;

    @BeforeEach
    void setUp() {
        testEventId = UUID.randomUUID();
        testConsumerName = "test-consumer";
    }

    /**
     * Test that a new event is not marked as processed.
     */
    @Test
    void testNewEventIsNotProcessed() {
        // Act
        boolean isProcessed = idempotencyService.hasProcessed(testEventId, testConsumerName);

        // Assert
        assertFalse(isProcessed);
    }

    /**
     * Test that marking an event as processed works correctly.
     */
    @Test
    void testMarkEventAsProcessed() {
        // Arrange
        String eventPayload = "{\"test\":\"payload\"}";

        // Act
        idempotencyService.markProcessed(testEventId, testConsumerName, eventPayload);

        // Assert
        assertTrue(idempotencyService.hasProcessed(testEventId, testConsumerName));
    }

    /**
     * Test idempotency: duplicate marking raises constraint violation.
     */
    @Test
    void testDuplicateMarkingRaisesConstraintViolation() {
        // Arrange
        String eventPayload = "{\"test\":\"payload\"}";
        idempotencyService.markProcessed(testEventId, testConsumerName, eventPayload);

        // Act & Assert - second marking should raise exception
        assertThrows(
                DataIntegrityViolationException.class,
                () -> idempotencyService.markProcessed(testEventId, testConsumerName, eventPayload)
        );
    }

    /**
     * Test that the same event can be processed by different consumers.
     */
    @Test
    void testSameEventProcessedByDifferentConsumers() {
        // Arrange
        String eventPayload = "{\"test\":\"payload\"}";
        String consumer1 = "consumer-1";
        String consumer2 = "consumer-2";

        // Act
        idempotencyService.markProcessed(testEventId, consumer1, eventPayload);
        idempotencyService.markProcessed(testEventId, consumer2, eventPayload);

        // Assert
        assertTrue(idempotencyService.hasProcessed(testEventId, consumer1));
        assertTrue(idempotencyService.hasProcessed(testEventId, consumer2));
        assertEquals(2, processedEventRepository.count());
    }

    /**
     * Test cleanup of old processed events.
     */
    @Test
    void testCleanupOldProcessedEvents() {
        // Arrange
        String eventPayload = "{\"test\":\"payload\"}";
        idempotencyService.markProcessed(testEventId, testConsumerName, eventPayload);

        // Act - cleanup events older than now (should delete all)
        long deletedCount = idempotencyService.cleanupOlderThan(Instant.now());

        // Assert
        assertEquals(1, deletedCount);
        assertEquals(0, processedEventRepository.count());
    }

    /**
     * Test that recent events are not cleaned up.
     */
    @Test
    void testRecentEventsAreNotCleanedUp() {
        // Arrange
        String eventPayload = "{\"test\":\"payload\"}";
        idempotencyService.markProcessed(testEventId, testConsumerName, eventPayload);

        // Act - cleanup events older than 1 hour ago (should not delete anything)
        long deletedCount = idempotencyService.cleanupOlderThan(
                Instant.now().minusSeconds(3600)
        );

        // Assert
        assertEquals(0, deletedCount);
        assertEquals(1, processedEventRepository.count());
    }

    /**
     * Test processed event count.
     */
    @Test
    void testGetProcessedCount() {
        // Arrange
        String eventPayload = "{\"test\":\"payload\"}";
        idempotencyService.markProcessed(UUID.randomUUID(), "consumer-1", eventPayload);
        idempotencyService.markProcessed(UUID.randomUUID(), "consumer-2", eventPayload);

        // Act
        long count = idempotencyService.getProcessedCount();

        // Assert
        assertEquals(2, count);
    }
}

