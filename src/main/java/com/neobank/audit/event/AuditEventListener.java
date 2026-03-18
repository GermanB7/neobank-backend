package com.neobank.audit.event;

import com.neobank.accounts.domain.events.AccountCreatedEvent;
import com.neobank.audit.service.AuditService;
import com.neobank.auth.domain.events.UserRegisteredEvent;
import com.neobank.transfers.domain.events.TransferCompletedEvent;
import com.neobank.transfers.domain.events.TransferRejectedByRiskEvent;
import com.neobank.transfers.domain.events.TransferReversedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to domain events and records corresponding audit events.
 *
 * This component demonstrates the event-driven architecture pattern by handling
 * audit side effects separately from core domain service logic.
 *
 * Benefits:
 * - Decouples audit recording from core transactional flows
 * - Makes audit logic reusable and testable independently
 * - Separates side effects from business logic
 * - Enables future async processing of audit events
 *
 * Processing:
 * - Listeners are invoked synchronously within the same transaction
 * - If audit recording fails, the transaction rolls back
 * - Each listener method is independent and handles one event type
 *
 * Future evolution:
 * - Listeners could be marked @Async for non-blocking audit recording
 * - Events could be persisted to an outbox table before returning
 * - Events could be published to Kafka for centralized audit logging
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Records audit event when a transfer is successfully completed.
     */
    @EventListener
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.debug("Handling TransferCompletedEvent: {}", event.getEventId());

        auditService.recordEvent(
                "TRANSFER_COMPLETED",
                event.getInitiatedByUserId(),
                null,  // email is not available at this point
                "TRANSFER",
                event.getTransferId().toString(),
                "SUCCESS",
                "Transfer completed: source=" + event.getSourceAccountId() +
                        " target=" + event.getTargetAccountId() +
                        " amount=" + event.getAmount() + " " + event.getCurrency()
        );
    }

    /**
     * Records audit event when a transfer is rejected by risk evaluation.
     */
    @EventListener
    public void onTransferRejectedByRisk(TransferRejectedByRiskEvent event) {
        log.debug("Handling TransferRejectedByRiskEvent: {}", event.getEventId());

        auditService.recordEvent(
                "TRANSFER_REJECTED_BY_RISK",
                event.getInitiatedByUserId(),
                null,  // email is not available at this point
                "RISK_EVALUATION",
                event.getRiskEvaluationId().toString(),
                "REJECTED",
                event.getReason()
        );
    }

    /**
     * Records audit event when a new account is created.
     */
    @EventListener
    public void onAccountCreated(AccountCreatedEvent event) {
        log.debug("Handling AccountCreatedEvent: {}", event.getEventId());

        auditService.recordEvent(
                "ACCOUNT_CREATED",
                event.getOwnerId(),
                null,  // email is not available at this point
                "ACCOUNT",
                event.getAccountId().toString(),
                "SUCCESS",
                "Account created with type=" + event.getAccountType() +
                        " currency=" + event.getCurrency() +
                        " accountNumber=" + event.getAccountNumber()
        );
    }

    /**
     * Records audit event when a new user registers.
     */
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.debug("Handling UserRegisteredEvent: {}", event.getEventId());

        auditService.recordEvent(
                "USER_REGISTERED",
                event.getUserId(),
                event.getEmail(),
                "USER",
                event.getUserId().toString(),
                "SUCCESS",
                "User registered"
        );
    }

    /**
     * Records audit event when a transfer is reversed.
     */
    @EventListener
    public void onTransferReversed(TransferReversedEvent event) {
        log.debug("Handling TransferReversedEvent: {}", event.getEventId());

        auditService.recordEvent(
                "TRANSFER_REVERSED",
                event.getInitiatedByUserId(),
                null,
                "TRANSFER",
                event.getReversalTransferId().toString(),
                "SUCCESS",
                "Reversal completed: originalTransferId=" + event.getOriginalTransferId() +
                        " reversalTransferId=" + event.getReversalTransferId() +
                        " amount=" + event.getAmount() + " " + event.getCurrency() +
                        " reason=" + event.getReason()
        );
    }
}
