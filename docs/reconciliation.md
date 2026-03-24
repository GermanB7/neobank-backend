# Reconciliation

## Purpose

Detect consistency drift between account balances, transfer terminal states, and ledger linkage, then provide actionable discrepancy reports for operators.

## What "drift" means in this system

Drift is any state where persisted data violates expected invariants across modules. Examples:

- account balance does not match ledger-derived net for the same account/currency
- completed transfer has no ledger transaction
- ledger transaction points to missing or non-terminal transfer
- reversal linkage is missing/invalid/duplicated

## Sources of truth compared

`ReconciliationService#runFullReconciliation` compares:

- `accounts` balances
- `transfers` status/kind/linkage
- `ledger_transactions` and `ledger_entries`

Results are persisted in:

- `reconciliation_reports`
- `reconciliation_discrepancies`

Schema reference: `src/main/resources/db/migration/V9__reconciliation.sql`.

## When reconciliation runs

Current implementation is operator-triggered via admin API:

- `POST /reconciliation/run`
- `GET /reconciliation/reports`
- `GET /reconciliation/reports/{reportId}/discrepancies`

This is intentionally explicit: reconciliation runs are auditable administrative actions, not hidden background jobs.

## Discrepancy types and what they may indicate

- `ACCOUNT_LEDGER_BALANCE_MISMATCH`: manual data changes, missed ledger writes, or incident side effects
- `COMPLETED_TRANSFER_WITHOUT_LEDGER`: transactional inconsistency or out-of-band mutation
- `LEDGER_WITHOUT_VALID_TRANSFER`: orphan ledger transaction or invalid transfer status linkage
- `REVERSAL_LINK_INCONSISTENCY`: broken reversal chain integrity
- `REVERSAL_WITHOUT_LEDGER`: compensating transfer completed without ledger evidence
- `DUPLICATE_REVERSAL`: control failure allowing multiple reversals for one original transfer

## Automated vs manual response

Automated:

- discrepancy detection and report persistence
- metrics increment (`reconciliation runs`, `discrepancy count`)
- admin access audit event

Manual/operator-driven:

- incident classification and escalation
- root-cause analysis
- corrective action (forward-fix, controlled reversal, or data repair under runbook)
- evidence capture for postmortem

## Operational implications

- reconciliation should run after incidents, migration anomalies, or suspicious financial discrepancies
- reports are evidence artifacts for incident review and compliance-style audit trails
- repeated mismatch patterns indicate boundary weaknesses that should be addressed via ADR/runbook updates

## Supporting code and tests

- `src/main/java/com/neobank/reconciliation/service/ReconciliationService.java`
- `src/main/java/com/neobank/reconciliation/api/controller/ReconciliationController.java`
- `src/main/resources/db/migration/V9__reconciliation.sql`
- `src/test/java/com/neobank/reconciliation/api/ReconciliationIntegrationTest.java`
- `docs/runbook.md`

