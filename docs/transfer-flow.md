# Transfer Flow

## Purpose

Define the transfer lifecycle from API request to durable completion, including validation order, transactional guarantees, and failure behavior.

## Domain boundary

- Entry point: `POST /transfers` in `TransferController`
- Core orchestrator: `TransferService#createTransfer`
- Ledger writer: `LedgerService#recordCompletedTransfer`
- Risk gate: `RiskEvaluationService#evaluateAndPersist`

## Preconditions

Before any balance mutation, the flow requires:

1. authenticated user context
2. source and target account ids are different
3. positive normalized amount (scale and format normalized)
4. optional idempotency key consistency (if provided)

## Validation and execution order

`TransferService#createTransfer` executes validations in this order:

1. request shape checks (`source != target`, amount normalization)
2. idempotency lookup before locking
3. deterministic account row locking (`findByIdForUpdate`) to avoid deadlocks
4. idempotency lookup again after lock acquisition (race protection)
5. ownership and account status checks:
   - initiator must own source account
   - both accounts must be `ACTIVE`
   - source/target currency must match
   - source balance must cover amount
6. risk evaluation persistence
7. transfer record creation (`PENDING`)
8. account balance mutation (debit source, credit target)
9. transfer marked `COMPLETED`
10. ledger transaction + entries persistence
11. domain event publication (`TransferCompletedEvent`) for outbox path

## Ownership and access control boundary

- API-level authn/authz is enforced by `SecurityConfig` + JWT filter.
- Domain-level ownership is enforced in transfer service (`sourceAccount.ownerId == currentUser.id`).
- Admin is required for reversal endpoint (`POST /transfers/{transferId}/reverse`).

## Transaction boundary

The core transfer execution is one transactional unit (`@Transactional` on `createTransfer`):

- transfer state
- account balances
- ledger records
- domain event persistence path

No partial debit-only or credit-only durable outcome is accepted.

## Failure semantics

- Input/ownership/business failures return 4xx with no money movement committed.
- Risk rejection throws `RiskPolicyViolationException` and returns `422`; transfer is not completed.
- Runtime exceptions during transactional work roll back transfer/account/ledger writes.
- Async publication failures after commit are handled by outbox retry/recovery, not by mutating committed transfer rows.

## Relationship to reversal and reconciliation

- Reversal is a separate admin-controlled flow (`TransferReversalService`) that creates compensating transfer + ledger records and marks the original transfer as `REVERSED`.
- Reconciliation validates:
  - completed transfers have ledger transactions
  - ledger transactions reference valid terminal transfers
  - reversal linkage constraints hold

References:

- `docs/reversal-flow.md`
- `docs/reconciliation.md`

## Supporting code and tests

- `src/main/java/com/neobank/transfers/service/TransferService.java`
- `src/main/java/com/neobank/transfers/service/TransferReversalService.java`
- `src/main/java/com/neobank/ledger/service/LedgerService.java`
- `src/main/resources/db/migration/V3__transfers.sql`
- `src/main/resources/db/migration/V4__ledger.sql`
- `src/main/resources/db/migration/V8__transfer_reversals.sql`
- `src/test/java/com/neobank/transfers/api/TransfersIntegrationTest.java`
- `src/test/java/com/neobank/reconciliation/api/ReconciliationIntegrationTest.java`

