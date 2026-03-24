# ADR-006: Operational Recovery Model (Detect, Contain, Correct)

- Status: Accepted
- Date: 2026-03-19

## Context

The system has mixed synchronous and asynchronous behavior (transactional core + outbox/Kafka). Failures are unavoidable; correctness depends on clear recovery workflow, not only prevention.

## Decision

Use an explicit operational recovery model with three phases:

1. Detect: health/metrics/alerts, outbox failure visibility, reconciliation reports.
2. Contain: stop unsafe operations, classify severity, protect data via backup.
3. Correct: apply forward-fix, controlled reversal, or data repair guided by runbook.

Recovery actions are treated as auditable operational procedures, not ad-hoc manual edits.

## Alternatives considered

1. Best-effort local fixes without standardized runbook.
2. Purely automated remediation for all discrepancy classes.
3. Incident handling delegated entirely to infrastructure-level restarts.

## Consequences

### Positive

- Consistent incident response under pressure.
- Better audit trail for postmortem and interview-grade operational evidence.
- Clear handoff between engineering and operations tasks.

### Negative

- Requires ongoing runbook maintenance.
- Increases process overhead for minor incidents.

## Evidence

- `docs/runbook.md`
- `docs/reconciliation.md`
- `docs/outbox-kafka-flow.md`
- `ops/backup/`
- `ops/load/`
- `monitoring/`

