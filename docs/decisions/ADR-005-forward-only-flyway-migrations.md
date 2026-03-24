# ADR-005: Forward-Only Flyway Migrations

- Status: Accepted
- Date: 2026-03-19

## Context

Schema changes in a financial backend must prioritize recoverability and auditability over convenience. Rollback-by-down-migration can hide partial effects and is risky under concurrent traffic.

## Decision

Adopt forward-only migration governance with Flyway:

- versioned migrations in `db/migration`
- no destructive `clean` in production profiles
- no out-of-order migration execution
- precheck/postcheck operational gates before and after deploy

## Alternatives considered

1. Bidirectional up/down migration scripts.
2. Manual SQL patching during incidents.
3. Auto-DDL schema evolution via ORM.

## Consequences

### Positive

- Predictable migration history and reproducible environments.
- Lower risk of hidden rollback side effects.
- Clear operator workflow for fail/repair/redeploy.

### Negative

- Fixes require additional forward migrations.
- Incident response may be slower when teams expect quick rollback scripts.

## Evidence

- `src/main/resources/application-prod.yml`
- `src/main/resources/db/migration/`
- `ops/migration/precheck.ps1`
- `ops/migration/postcheck.ps1`
- `docs/deployment.md`

