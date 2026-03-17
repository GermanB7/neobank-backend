CREATE TABLE IF NOT EXISTS risk_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID,
    source_account_id UUID NOT NULL,
    initiated_by_user_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    risk_score INT NOT NULL,
    triggered_rules VARCHAR(255) NOT NULL,
    reason VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_risk_evaluations_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_risk_evaluations_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id) ON DELETE SET NULL,
    CONSTRAINT fk_risk_evaluations_source_account FOREIGN KEY (source_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_risk_evaluations_initiated_by FOREIGN KEY (initiated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_risk_evaluations_source_account_id ON risk_evaluations(source_account_id);
CREATE INDEX IF NOT EXISTS idx_risk_evaluations_initiated_by_user_id ON risk_evaluations(initiated_by_user_id);
CREATE INDEX IF NOT EXISTS idx_risk_evaluations_transfer_id ON risk_evaluations(transfer_id);

