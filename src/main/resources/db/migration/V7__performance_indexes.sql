-- Sprint 9: Performance optimizations
-- Add indexes for frequently queried patterns in risk evaluation and transfer queries

-- Index for risk evaluation time-windowed queries
-- Used by RiskEvaluationService to check velocity/daily limits within time windows
CREATE INDEX IF NOT EXISTS idx_transfers_created_at
    ON transfers(created_at DESC)
    WHERE status = 'COMPLETED';

-- Index for composite queries on transfers status and creation time
-- Helps with risk policy enforcement checks
CREATE INDEX IF NOT EXISTS idx_transfers_status_created_at
    ON transfers(status, created_at DESC);

-- Optimize user lookup in auth flow (supporting future caching invalidation)
CREATE INDEX IF NOT EXISTS idx_users_email_lower
    ON users(LOWER(email));

-- Index for account ownership queries used in account and transfer operations
CREATE INDEX IF NOT EXISTS idx_accounts_owner_id
    ON accounts(owner_id, created_at DESC);

