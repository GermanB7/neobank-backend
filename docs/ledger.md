# Ledger

## Purpose

Define the accounting model used to represent money movement in an auditable, reconciliation-friendly way.

## Accounting model in this project

The ledger is transaction-based and append-only:

- one `ledger_transaction` per transfer outcome
- at least two `ledger_entry` rows per ledger transaction
- each entry has account id, side (`DEBIT` or `CREDIT`), amount, currency

Project convention implemented in `LedgerService`:

- outgoing money from source account -> `CREDIT`
- incoming money to target account -> `DEBIT`

This is "double-entry-style" here: every durable money movement is represented by balanced opposite sides in one logical ledger transaction.

## Records created

For a completed standard transfer:

1. `transfers` row is marked `COMPLETED`
2. one ledger transaction row is created with `type=TRANSFER` and `related_transfer_id`
3. two ledger entries are created:
   - source account credit
   - target account debit

For reversal flows, a compensating transfer produces its own ledger transaction and entries.

## Invariants

`LedgerService#validateDraftTransaction` enforces:

- transfer-linked ledger transactions require `relatedTransferId`
- minimum two entries per transaction
- every amount is positive
- all entries in a transaction share a single currency
- total debits must equal total credits

These invariants are local to each ledger transaction and make imbalance detectable at write time.

## Transaction boundary and consistency

Ledger persistence is called from transfer/reversal transactional flows, so transfer state and ledger records commit (or roll back) together.

This prevents durable states such as "completed transfer without corresponding ledger transaction" in normal execution paths; reconciliation still verifies and flags drift after incidents/manual intervention.

## Auditability and forensic value

- ledger entries preserve the exact movement path per account
- `related_transfer_id` gives deterministic traceability from transfer API response to ledger records
- reversal does not erase original facts, so incident timelines remain reconstructible

## Why reversal is append/correct instead of mutate/delete

Financial correction requires preserving the original event and adding a compensating event:

- supports post-incident investigation
- avoids hidden history rewrites
- keeps reconciliation logic deterministic (original + compensating chain)

Decision record: `docs/decisions/ADR-003-double-entry-ledger-and-reversal.md`.

## Supporting code and tests

- `src/main/java/com/neobank/ledger/service/LedgerService.java`
- `src/main/java/com/neobank/transfers/service/TransferService.java`
- `src/main/java/com/neobank/transfers/service/TransferReversalService.java`
- `src/main/resources/db/migration/V4__ledger.sql`
- `src/main/resources/db/migration/V8__transfer_reversals.sql`
- `src/test/java/com/neobank/transfers/api/TransfersIntegrationTest.java`
- `src/test/java/com/neobank/reconciliation/api/ReconciliationIntegrationTest.java`

