CREATE TABLE IF NOT EXISTS reconciliation_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    scope VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    discrepancies_found INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS reconciliation_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL,
    type VARCHAR(60) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    expected_value TEXT,
    actual_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reconciliation_discrepancies_report
        FOREIGN KEY (report_id) REFERENCES reconciliation_reports(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_discrepancies_report_id
    ON reconciliation_discrepancies(report_id);

CREATE INDEX IF NOT EXISTS idx_reconciliation_discrepancies_type
    ON reconciliation_discrepancies(type);

CREATE INDEX IF NOT EXISTS idx_reconciliation_discrepancies_resource_type
    ON reconciliation_discrepancies(resource_type);

CREATE INDEX IF NOT EXISTS idx_reconciliation_discrepancies_created_at
    ON reconciliation_discrepancies(created_at);

