# Operations Runbook

This is the canonical operations runbook entry point.

## Operating model

Use a Detect -> Contain -> Correct cycle:

1. Detect via health, metrics, alerts, and reconciliation/outbox indicators.
2. Contain blast radius and classify incident severity.
3. Correct with runbook-guided forward-fix, controlled reversal, or rollback.

Decision record: `docs/decisions/ADR-006-operational-recovery-model.md`.

## Core procedures

1. Startup and health verification
2. Deployment precheck/deploy/postcheck
3. Incident triage and escalation
4. Backup and restore
5. Migration failure handling
6. Outbox failure recovery
7. Reconciliation run and discrepancy handling
8. Load sanity validation

## Incident severity model

- SEV-1: data-integrity or availability-critical failure; immediate coordinated response
- SEV-2: high error rates, backlog growth, or degraded critical path
- SEV-3: non-critical degradation requiring planned remediation
- SEV-4: low-impact operational gaps

## Primary operational assets

- `ops/backup/`
- `ops/migration/`
- `ops/load/`
- `monitoring/`

## Archived operational evidence

- `docs/archive/sprints/SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md`
- `docs/archive/sprints/SPRINT18_FAILURE_DRILLS.md`
- `docs/archive/sprints/SPRINT18_MIGRATION_GOVERNANCE.md`
- `docs/archive/sprints/SPRINT18_ROLLBACK_STRATEGY.md`

