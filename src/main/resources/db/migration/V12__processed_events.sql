-- V12: Idempotency Support for Kafka Consumers (Sprint 15)
-- Tracks which events have been processed by consumers
-- Prevents duplicate processing of the same event

CREATE TABLE IF NOT EXISTS processed_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    event_payload JSONB,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_event_consumer UNIQUE (event_id, consumer_name)
);

-- Index for idempotency check: find if event already processed by consumer
CREATE INDEX IF NOT EXISTS idx_processed_events_event_consumer
    ON processed_events(event_id, consumer_name);

-- Index for cleanup: old processed events
CREATE INDEX IF NOT EXISTS idx_processed_events_timestamp
    ON processed_events(processed_at);

