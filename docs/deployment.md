# Deployment

This document defines the canonical deployment model for the repository.

## Deployment principles

- forward-only schema evolution with Flyway
- mandatory backup before risky change
- precheck and postcheck gates around release
- explicit rollback vs forward-fix decision

## Standard flow

1. Create backup (`ops/backup/`).
2. Run migration precheck (`ops/migration/precheck.ps1`).
3. Deploy the new artifact/container.
4. Validate health, migration state, and error indicators.
5. Execute postcheck (`ops/migration/postcheck.ps1`).
6. If validation fails, use the runbook to decide rollback or forward-fix.

## Verification checklist

- backup artifact created and restorable
- Flyway schema history has no failed entries
- app health endpoints green after deployment
- no sustained 5xx increase or outbox failure growth

## Supporting references

- `docs/runbook.md`
- `ops/QUICK_REFERENCE.md`
- `docs/decisions/ADR-005-forward-only-flyway-migrations.md`
- `docs/archive/sprints/SPRINT17_DEPLOYMENT_GUIDE.md`
- `docs/archive/sprints/SPRINT18_MIGRATION_GOVERNANCE.md`
- `docs/archive/sprints/SPRINT18_ROLLBACK_STRATEGY.md`

