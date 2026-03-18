package com.neobank.audit.event;

import com.neobank.accounts.domain.events.AccountCreatedEvent;
import com.neobank.audit.domain.AuditEventEntity;
import com.neobank.audit.repository.AuditEventRepository;
import com.neobank.auth.domain.events.UserRegisteredEvent;
import com.neobank.transfers.domain.events.TransferCompletedEvent;
import com.neobank.transfers.domain.events.TransferRejectedByRiskEvent;
import com.neobank.transfers.domain.events.TransferReversedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for audit event listener.
 *
 * Verifies that:
 * 1. Domain events are correctly captured and converted to audit events
 * 2. Audit event listener processes all relevant domain events
 * 3. Audit entries are persisted correctly
 * 4. Side effects (audit logging) are cleanly separated from core domain logic
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditEventListenerTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private AuditEventRepository auditEventRepository;

    /**
     * Test that TransferCompletedEvent is captured and an audit event is recorded.
     */
    @Test
    @Transactional
    void testTransferCompletedEventTriggersAudit() {
        // Clear existing audit events
        auditEventRepository.deleteAll();

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

        // Publish event
        applicationEventPublisher.publishEvent(event);

        // Verify audit event was created
        List<AuditEventEntity> auditEvents = auditEventRepository.findAll();
        assertEquals(1, auditEvents.size(), "Should have created one audit event");

        AuditEventEntity auditEvent = auditEvents.get(0);
        assertEquals("TRANSFER_COMPLETED", auditEvent.getEventType());
        assertEquals(initiatedByUserId, auditEvent.getActorUserId());
        assertEquals("TRANSFER", auditEvent.getResourceType());
        assertEquals(transferId.toString(), auditEvent.getResourceId());
        assertEquals("SUCCESS", auditEvent.getOutcome());
        assertNotNull(auditEvent.getDetails());
        assertTrue(auditEvent.getDetails().contains("100.00"));
    }

    /**
     * Test that TransferRejectedByRiskEvent is captured and an audit event is recorded.
     */
    @Test
    @Transactional
    void testTransferRejectedByRiskEventTriggersAudit() {
        // Clear existing audit events
        auditEventRepository.deleteAll();

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

        // Publish event
        applicationEventPublisher.publishEvent(event);

        // Verify audit event was created
        List<AuditEventEntity> auditEvents = auditEventRepository.findAll();
        assertEquals(1, auditEvents.size(), "Should have created one audit event");

        AuditEventEntity auditEvent = auditEvents.get(0);
        assertEquals("TRANSFER_REJECTED_BY_RISK", auditEvent.getEventType());
        assertEquals(initiatedByUserId, auditEvent.getActorUserId());
        assertEquals("RISK_EVALUATION", auditEvent.getResourceType());
        assertEquals(riskEvaluationId.toString(), auditEvent.getResourceId());
        assertEquals("REJECTED", auditEvent.getOutcome());
        assertEquals(reason, auditEvent.getDetails());
    }

    /**
     * Test that AccountCreatedEvent is captured and an audit event is recorded.
     */
    @Test
    @Transactional
    void testAccountCreatedEventTriggersAudit() {
        // Clear existing audit events
        auditEventRepository.deleteAll();

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

        // Publish event
        applicationEventPublisher.publishEvent(event);

        // Verify audit event was created
        List<AuditEventEntity> auditEvents = auditEventRepository.findAll();
        assertEquals(1, auditEvents.size(), "Should have created one audit event");

        AuditEventEntity auditEvent = auditEvents.get(0);
        assertEquals("ACCOUNT_CREATED", auditEvent.getEventType());
        assertEquals(ownerId, auditEvent.getActorUserId());
        assertEquals("ACCOUNT", auditEvent.getResourceType());
        assertEquals(accountId.toString(), auditEvent.getResourceId());
        assertEquals("SUCCESS", auditEvent.getOutcome());
        assertNotNull(auditEvent.getDetails());
        assertTrue(auditEvent.getDetails().contains("CHECKING"));
        assertTrue(auditEvent.getDetails().contains("USD"));
    }

    /**
     * Test that UserRegisteredEvent is captured and an audit event is recorded.
     */
    @Test
    @Transactional
    void testUserRegisteredEventTriggersAudit() {
        // Clear existing audit events
        auditEventRepository.deleteAll();

        UUID userId = UUID.randomUUID();
        String email = "newuser@example.com";

        UserRegisteredEvent event = new UserRegisteredEvent(userId, email);

        // Publish event
        applicationEventPublisher.publishEvent(event);

        // Verify audit event was created
        List<AuditEventEntity> auditEvents = auditEventRepository.findAll();
        assertEquals(1, auditEvents.size(), "Should have created one audit event");

        AuditEventEntity auditEvent = auditEvents.get(0);
        assertEquals("USER_REGISTERED", auditEvent.getEventType());
        assertEquals(userId, auditEvent.getActorUserId());
        assertEquals(email, auditEvent.getActorEmail());
        assertEquals("USER", auditEvent.getResourceType());
        assertEquals(userId.toString(), auditEvent.getResourceId());
        assertEquals("SUCCESS", auditEvent.getOutcome());
    }

    /**
     * Test that multiple events are all captured and converted to audit events.
     */
    @Test
    @Transactional
    void testMultipleEventsAllTrigggerAudit() {
        // Clear existing audit events
        auditEventRepository.deleteAll();

        // Publish multiple events
        UserRegisteredEvent userEvent = new UserRegisteredEvent(UUID.randomUUID(), "user1@example.com");
        applicationEventPublisher.publishEvent(userEvent);

        AccountCreatedEvent accountEvent = new AccountCreatedEvent(
                UUID.randomUUID(),
                "ACC123",
                userEvent.getUserId(),
                "CHECKING",
                "USD"
        );
        applicationEventPublisher.publishEvent(accountEvent);

        TransferCompletedEvent transferEvent = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                userEvent.getUserId(),
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );
        applicationEventPublisher.publishEvent(transferEvent);

        // Verify all audit events were created
        List<AuditEventEntity> auditEvents = auditEventRepository.findAll();
        assertEquals(3, auditEvents.size(), "Should have created three audit events");

        // Verify event types
        long userRegisteredCount = auditEvents.stream()
                .filter(e -> "USER_REGISTERED".equals(e.getEventType()))
                .count();
        long accountCreatedCount = auditEvents.stream()
                .filter(e -> "ACCOUNT_CREATED".equals(e.getEventType()))
                .count();
        long transferCompletedCount = auditEvents.stream()
                .filter(e -> "TRANSFER_COMPLETED".equals(e.getEventType()))
                .count();

        assertEquals(1, userRegisteredCount);
        assertEquals(1, accountCreatedCount);
        assertEquals(1, transferCompletedCount);
    }

    /**
     * Test that TransferReversedEvent is captured and an audit event is recorded.
     */
    @Test
    @Transactional
    void testTransferReversedEventTriggersAudit() {
        auditEventRepository.deleteAll();

        UUID originalTransferId = UUID.randomUUID();
        UUID reversalTransferId = UUID.randomUUID();
        UUID initiatedByUserId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        TransferReversedEvent event = new TransferReversedEvent(
                originalTransferId,
                reversalTransferId,
                initiatedByUserId,
                sourceAccountId,
                targetAccountId,
                amount,
                "USD",
                "duplicate payment",
                Instant.now()
        );

        applicationEventPublisher.publishEvent(event);

        List<AuditEventEntity> auditEvents = auditEventRepository.findAll();
        assertEquals(1, auditEvents.size(), "Should have created one audit event");

        AuditEventEntity auditEvent = auditEvents.get(0);
        assertEquals("TRANSFER_REVERSED", auditEvent.getEventType());
        assertEquals(initiatedByUserId, auditEvent.getActorUserId());
        assertEquals("TRANSFER", auditEvent.getResourceType());
        assertEquals(reversalTransferId.toString(), auditEvent.getResourceId());
        assertEquals("SUCCESS", auditEvent.getOutcome());
        assertNotNull(auditEvent.getDetails());
        assertTrue(auditEvent.getDetails().contains(originalTransferId.toString()));
    }
}
