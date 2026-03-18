package com.neobank.analytics.domain;

/**
 * Transfer status in analytics context.
 *
 * Represents the outcome of a transfer from analytics perspective.
 */
public enum TransferAnalyticsStatus {
    COMPLETED,  // Transfer completed successfully
    REJECTED,   // Transfer rejected by risk evaluation
    REVERSED    // Transfer was reversed/cancelled
}

