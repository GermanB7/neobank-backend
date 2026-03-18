package com.neobank.shared.domain;

/**
 * Status enum for outbox events.
 *
 * Lifecycle:
 * - PENDING: Initial state, ready to be processed
 * - PROCESSING: Currently being processed (prevents duplicate processing)
 * - PROCESSED: Successfully published to all listeners
 * - FAILED: Failed after max_attempts retries (requires manual intervention or dead letter)
 */
public enum OutboxStatus {
    /**
     * Event is pending processing.
     * Processor will pick it up and transition to PROCESSING.
     */
    PENDING,

    /**
     * Event is currently being processed.
     * Protects against duplicate processing in concurrent scenarios.
     * If processor crashes while in this state, event can be recovered by timeout logic.
     */
    PROCESSING,

    /**
     * Event has been successfully processed and published to all listeners.
     * Final happy-path state.
     */
    PROCESSED,

    /**
     * Event failed after exceeding max_attempts.
     * Requires investigation and manual intervention or dead-letter processing.
     * Error message is stored in the error_message column.
     */
    FAILED;

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isProcessing() {
        return this == PROCESSING;
    }

    public boolean isProcessed() {
        return this == PROCESSED;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    public boolean isTerminal() {
        return this == PROCESSED || this == FAILED;
    }
}

