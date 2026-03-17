CREATE TABLE IF NOT EXISTS transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_account_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transfer_reference VARCHAR(255),
    initiated_by_user_id UUID NOT NULL,
    idempotency_key VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_source_target_different CHECK (source_account_id <> target_account_id),
    CONSTRAINT fk_transfers_source_account FOREIGN KEY (source_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_target_account FOREIGN KEY (target_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_initiated_by FOREIGN KEY (initiated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_transfers_source_account_id ON transfers(source_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_target_account_id ON transfers(target_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_initiated_by_user_id ON transfers(initiated_by_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_transfers_initiated_user_idempotency
    ON transfers(initiated_by_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

