package com.neobank.outbox.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.shared.domain.DomainEvent;
import com.neobank.outbox.domain.OutboxStatus;
import com.neobank.transfers.domain.events.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OutboxEventPublisher.
 *
 * Tests verify that:
 * 1. Events are correctly serialized to JSON
 * 2. OutboxEventEntity is created with correct fields
 * 3. Entity is persisted via repository
 * 4. Aggregate type and ID are correctly extracted
 * 5. Null events are rejected
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventEntity savedEntity;

    private OutboxEventPublisher outboxEventPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        outboxEventPublisher = new OutboxEventPublisher(outboxEventRepository, objectMapper);
    }

    @Test
    void testPublishEventPersistsWithCorrectData() {
        // Given
        UUID transferId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                sourceAccountId,
                targetAccountId,
                userId,
                amount,
                currency,
                Instant.now()
        );

        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenReturn(savedEntity);

        // When
        outboxEventPublisher.publishEvent(event);

        // Then: Verify entity was created and saved
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals("TransferCompleted", saved.getEventType());
        assertEquals("Transfer", saved.getAggregateType());
        assertEquals(transferId, saved.getAggregateId());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttemptCount());
        assertEquals(3, saved.getMaxAttempts());
        assertNotNull(saved.getEventPayload());
        assertNull(saved.getProcessedAt());
    }

    @Test
    void testPublishEventSerializesPayload() throws Exception {
        // Given
        UUID transferId = UUID.randomUUID();
        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("50.00"),
                "EUR",
                Instant.now()
        );

        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenReturn(savedEntity);

        // When
        outboxEventPublisher.publishEvent(event);

        // Then: Verify payload is valid JSON containing event fields
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        String payload = captor.getValue().getEventPayload();
        assertNotNull(payload);

        // Verify payload can be parsed and contains expected fields
        JsonNode root = objectMapper.readTree(payload);
        assertEquals(transferId.toString(), root.get("transferId").asText());
        assertEquals("50.0", root.get("amount").asText());
        assertEquals("EUR", root.get("currency").asText());
    }

    @Test
    void testPublishEventThrowsOnNullEvent() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> outboxEventPublisher.publishEvent(null)
        );

        // Verify repository was never called
        verify(outboxEventRepository, times(0)).save(any());
    }

    @Test
    void testAggregateTypeExtraction() {
        // Given: Events with different naming patterns
        testAggregateTypeFor(
                new TransferCompletedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        BigDecimal.TEN,
                        "USD",
                        Instant.now()
                ),
                "Transfer"
        );
    }

    private void testAggregateTypeFor(DomainEvent event, String expectedType) {
        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenReturn(savedEntity);

        outboxEventPublisher.publishEvent(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        assertEquals(expectedType, captor.getValue().getAggregateType());
    }

    @Test
    void testAggregateIdExtraction() {
        // Given
        UUID transferId = UUID.randomUUID();
        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN,
                "USD",
                Instant.now()
        );

        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenReturn(savedEntity);

        // When
        outboxEventPublisher.publishEvent(event);

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        assertEquals(transferId, captor.getValue().getAggregateId());
    }

    @Test
    void testPublishEventWithCreatedAtTimestamp() {
        // Given
        TransferCompletedEvent event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN,
                "USD",
                Instant.now()
        );

        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenReturn(savedEntity);

        Instant beforePublish = Instant.now();

        // When
        outboxEventPublisher.publishEvent(event);

        Instant afterPublish = Instant.now();

        // Then
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        Instant createdAt = captor.getValue().getCreatedAt();
        assertNotNull(createdAt);
        assertTrue(createdAt.isAfter(beforePublish) || createdAt.equals(beforePublish));
        assertTrue(createdAt.isBefore(afterPublish) || createdAt.equals(afterPublish));
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
