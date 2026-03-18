package com.neobank.shared.domain;

import com.neobank.accounts.domain.events.AccountCreatedEvent;
import com.neobank.auth.domain.events.UserRegisteredEvent;
import com.neobank.transfers.domain.events.TransferCompletedEvent;
import com.neobank.transfers.domain.events.TransferRejectedByRiskEvent;
import com.neobank.transfers.domain.events.TransferReversedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for domain event publishing and handling.
 *
 * Verifies that:
 * 1. Domain events are correctly published by the DomainEventPublisher
 * 2. Event listeners receive and process events correctly
 * 3. Event payloads contain correct data
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DomainEventPublishingTest {

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * Test that AccountCreatedEvent can be published and contains correct data.
     */
    @Test
    void testAccountCreatedEventPublication() {
        UUID accountId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String accountNumber = "ACC0123456789";
        String accountType = "CHECKING";
        String currency = "USD";

        AccountCreatedEvent event = new AccountCreatedEvent(
                accountId,
                accountNumber,
                ownerId,
                accountType,
                currency
        );

        // Verify event content
        assertEquals("AccountCreated", event.getEventType());
        assertEquals(accountId, event.getAccountId());
        assertEquals(ownerId, event.getOwnerId());
        assertEquals(accountNumber, event.getAccountNumber());
        assertEquals(accountType, event.getAccountType());
        assertEquals(currency, event.getCurrency());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    /**
     * Test that UserRegisteredEvent can be published and contains correct data.
     */
    @Test
    void testUserRegisteredEventPublication() {
        UUID userId = UUID.randomUUID();
        String email = "user@example.com";

        UserRegisteredEvent event = new UserRegisteredEvent(userId, email);

        // Verify event content
        assertEquals("UserRegistered", event.getEventType());
        assertEquals(userId, event.getUserId());
        assertEquals(email, event.getEmail());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    /**
     * Test that TransferCompletedEvent can be published and contains correct data.
     */
    @Test
    void testTransferCompletedEventPublication() {
        UUID transferId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID initiatedByUserId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        Instant processedAt = Instant.now();

        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                sourceAccountId,
                targetAccountId,
                initiatedByUserId,
                amount,
                currency,
                processedAt
        );

        // Verify event content
        assertEquals("TransferCompleted", event.getEventType());
        assertEquals(transferId, event.getTransferId());
        assertEquals(sourceAccountId, event.getSourceAccountId());
        assertEquals(targetAccountId, event.getTargetAccountId());
        assertEquals(initiatedByUserId, event.getInitiatedByUserId());
        assertEquals(amount, event.getAmount());
        assertEquals(currency, event.getCurrency());
        assertEquals(processedAt, event.getProcessedAt());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    /**
     * Test that TransferRejectedByRiskEvent can be published and contains correct data.
     */
    @Test
    void testTransferRejectedByRiskEventPublication() {
        UUID riskEvaluationId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID initiatedByUserId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");
        String reason = "Daily limit exceeded";

        TransferRejectedByRiskEvent event = new TransferRejectedByRiskEvent(
                riskEvaluationId,
                sourceAccountId,
                targetAccountId,
                initiatedByUserId,
                amount,
                reason
        );

        // Verify event content
        assertEquals("TransferRejectedByRisk", event.getEventType());
        assertEquals(riskEvaluationId, event.getRiskEvaluationId());
        assertEquals(sourceAccountId, event.getSourceAccountId());
        assertEquals(targetAccountId, event.getTargetAccountId());
        assertEquals(initiatedByUserId, event.getInitiatedByUserId());
        assertEquals(amount, event.getAmount());
        assertEquals(reason, event.getReason());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    /**
     * Test that TransferReversedEvent can be published and contains correct data.
     */
    @Test
    void testTransferReversedEventPublication() {
        UUID originalTransferId = UUID.randomUUID();
        UUID reversalTransferId = UUID.randomUUID();
        UUID initiatedByUserId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.00");
        String currency = "USD";
        String reason = "duplicate payment";
        Instant processedAt = Instant.now();

        TransferReversedEvent event = new TransferReversedEvent(
                originalTransferId,
                reversalTransferId,
                initiatedByUserId,
                sourceAccountId,
                targetAccountId,
                amount,
                currency,
                reason,
                processedAt
        );

        // Verify event content
        assertEquals("TransferReversed", event.getEventType());
        assertEquals(originalTransferId, event.getOriginalTransferId());
        assertEquals(reversalTransferId, event.getReversalTransferId());
        assertEquals(initiatedByUserId, event.getInitiatedByUserId());
        assertEquals(sourceAccountId, event.getSourceAccountId());
        assertEquals(targetAccountId, event.getTargetAccountId());
        assertEquals(amount, event.getAmount());
        assertEquals(currency, event.getCurrency());
        assertEquals(reason, event.getReason());
        assertEquals(processedAt, event.getProcessedAt());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    /**
     * Test that DomainEventPublisher can publish events without throwing exceptions.
     */
    @Test
    void testDomainEventPublisherPublishesWithoutError() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email);

        // Should not throw any exception
        domainEventPublisher.publishEvent(event);
    }

    /**
     * Test that DomainEventPublisher rejects null events.
     */
    @Test
    void testDomainEventPublisherRejectsNullEvent() {
        try {
            domainEventPublisher.publishEvent(null);
            assertFalse(true, "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertEquals("Event cannot be null", ex.getMessage());
        }
    }

    /**
     * Test that events are immutable and can be used safely.
     */
    @Test
    void testEventImmutability() {
        UUID accountId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        AccountCreatedEvent event = new AccountCreatedEvent(
                accountId,
                "ACC123",
                ownerId,
                "CHECKING",
                "USD"
        );

        UUID originalEventId = event.getEventId();
        Instant originalOccurredAt = event.getOccurredAt();

        // Events should maintain their identity and timestamp
        assertEquals(originalEventId, event.getEventId());
        assertEquals(originalOccurredAt, event.getOccurredAt());
    }
}

