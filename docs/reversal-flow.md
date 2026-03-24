# Reversal and Compensation Flow

## Objective
Define how incorrect or exceptional transfer outcomes are corrected without deleting financial history.

## Flow
1. Identify transfer requiring correction.
2. Validate reversal eligibility and policy constraints.
3. Persist reversal/compensation records.
4. Keep traceable linkage between original transfer and reversal action.
5. Surface result for audit and reconciliation.

## Why this approach
- Preserves audit trail integrity
- Supports post-incident forensic analysis
- Avoids destructive rewrites of financial history

## Evidence
- `src/main/resources/db/migration/V8__transfer_reversals.sql`
- `src/test/java/com/neobank/transfers/api/TransfersIntegrationTest.java`
- `docs/decisions/ADR-003-double-entry-ledger-and-reversal.md`

