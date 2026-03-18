-- V11: Outbox Pattern Implementation
-- Creates durable event table for transactional event publishing
-- Solves dual-write problem by persisting events with business state in same transaction

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,

    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempt_count >= 0 AND max_attempts > 0)
);

-- Index for processor: quickly find PENDING events to process
CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_events(status, created_at)
    WHERE status = 'PENDING';

-- Index for finding events by aggregate (for debugging/admin queries)
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);

-- Index for audit: finding processed events by timestamp
CREATE INDEX IF NOT EXISTS idx_outbox_processed_at
    ON outbox_events(processed_at)
    WHERE processed_at IS NOT NULL;

-- Index to detect processing delays (events stuck in PROCESSING)
CREATE INDEX IF NOT EXISTS idx_outbox_processing
    ON outbox_events(status, last_attempt_at)
    WHERE status = 'PROCESSING';

-- Index for finding events that failed
CREATE INDEX IF NOT EXISTS idx_outbox_failed
    ON outbox_events(status, last_attempt_at)
    WHERE status = 'FAILED';

