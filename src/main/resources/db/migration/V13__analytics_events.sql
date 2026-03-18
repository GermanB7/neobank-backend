-- V13: Analytics Events Table (Sprint 15)
-- Stores transfer analytics from Kafka consumers
-- Used by analytics consumer to track transfer patterns and metrics

CREATE TABLE IF NOT EXISTS analytics_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    transfer_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    initiated_by_user_id UUID,
    processed_at TIMESTAMPTZ,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_event_type CHECK (event_type IN (
        'TransferCompleted',
        'TransferRejectedByRisk',
        'TransferReversed'
    )),
    CONSTRAINT ck_status CHECK (status IN (
        'COMPLETED',
        'REJECTED',
        'REVERSED'
    ))
);

-- Index for analytics queries
CREATE INDEX IF NOT EXISTS idx_analytics_event_type
    ON analytics_events(event_type);

-- Index for time-series queries (reports)
CREATE INDEX IF NOT EXISTS idx_analytics_received_at
    ON analytics_events(received_at DESC);

-- Index for transfer traceability
CREATE INDEX IF NOT EXISTS idx_analytics_transfer_id
    ON analytics_events(transfer_id);

-- Index for user analytics
CREATE INDEX IF NOT EXISTS idx_analytics_user_id
    ON analytics_events(initiated_by_user_id);

-- Composite index for daily reports
CREATE INDEX IF NOT EXISTS idx_analytics_event_date
    ON analytics_events(event_type, DATE(received_at));

