ALTER TABLE transfers
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS original_transfer_id UUID;

ALTER TABLE transfers
    ADD CONSTRAINT fk_transfers_original_transfer
        FOREIGN KEY (original_transfer_id) REFERENCES transfers(id) ON DELETE RESTRICT;

ALTER TABLE transfers
    ADD CONSTRAINT ck_transfers_kind_original_link
        CHECK (
            (kind = 'STANDARD' AND original_transfer_id IS NULL)
            OR (kind = 'REVERSAL' AND original_transfer_id IS NOT NULL)
        );

CREATE INDEX IF NOT EXISTS idx_transfers_kind ON transfers(kind);
CREATE INDEX IF NOT EXISTS idx_transfers_original_transfer_id ON transfers(original_transfer_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transfers_single_reversal_per_original
    ON transfers(original_transfer_id)
    WHERE kind = 'REVERSAL';

