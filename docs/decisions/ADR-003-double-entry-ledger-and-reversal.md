# ADR-003: Ledger-Centric Transfer Model with Reversal Support

- Status: Accepted
- Date: 2026-03-19

## Context

A finance-like transfer system must preserve auditability and permit controlled correction without silently mutating historical records.

## Decision

Represent transfer outcomes with ledger-oriented entries and implement reversal/compensation flows as explicit follow-up records rather than destructive update of historical facts.

## Consequences

### Positive
- Better audit trail for money movement
- Easier reconciliation and forensic analysis
- Safer correction model under operational incidents

### Negative
- More complex data model and query patterns
- Requires clear semantics for original vs reversal entries

## Mitigations

- Keep transfer and ledger writes in a single transactional boundary
- Document reversal semantics and reconciliation rules
- Validate invariants with integration tests

## Evidence

- `src/main/resources/db/migration/V3__transfers.sql`
- `src/main/resources/db/migration/V4__ledger.sql`
- `src/main/resources/db/migration/V8__transfer_reversals.sql`
- `src/test/java/com/neobank/transfers/api/TransfersIntegrationTest.java`
- `src/test/java/com/neobank/reconciliation/api/ReconciliationIntegrationTest.java`

