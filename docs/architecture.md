# Architecture

This is the canonical system architecture document for Neobank Backend.

## Purpose

Define module boundaries, data ownership, and failure semantics for a correctness-first backend that handles money movement and operational recovery.

## Architectural style

Neobank uses a modular monolith: one deployable Spring Boot application with explicit package-level domain boundaries under `src/main/java/com/neobank/*`.

Why this over early microservices:

- transfer and ledger operations require strong transaction guarantees
- a single DB transaction boundary reduces failure modes for core money flow
- domain boundaries can mature before introducing network/distributed transaction complexity

Decision record: `docs/decisions/ADR-001-modular-monolith-over-microservices.md`.

## Modules and responsibilities

- `auth`: identity, JWT lifecycle, role model, session/logout controls
- `accounts`: account lifecycle and balance state ownership
- `transfers`: transfer orchestration, idempotency checks, reversal entry points
- `ledger`: immutable accounting records and balancing validation
- `risk`: policy evaluation for transfer eligibility
- `reconciliation`: drift detection across accounts, transfers, ledger links
- `outbox` + `messaging`: durable event publication and Kafka dispatch
- `audit` + `observability`: security-sensitive audit trail, metrics, health signals

## Data ownership

- PostgreSQL is the source of truth for transactional state (`accounts`, `transfers`, `ledger_*`, `outbox_events`, `reconciliation_*`).
- Redis is auxiliary state (cache/session/rate-limit support), not financial source of truth.
- Kafka is an integration channel for downstream consumers, not an authoritative store.

Schema evolution is managed via Flyway migrations in `src/main/resources/db/migration/`.

## Request flow shape

Typical write path (example: transfer):

1. Request enters controller (for example `TransferController`).
2. `SecurityConfig` + `JwtAuthFilter` enforce authentication, role policy, and endpoint access.
3. Domain service validates ownership/business constraints (`TransferService#createTransfer`).
4. Stateful changes are committed in a single DB transaction (transfer state, account balances, ledger records, domain event persistence via outbox publisher).
5. API response is returned after commit; asynchronous integration continues through outbox processing.

## Transactional vs asynchronous boundaries

Transactional (synchronous) boundary:

- transfer lifecycle state mutation
- account balance mutation
- ledger transaction and entries persistence
- outbox row persistence through domain event publication path

Asynchronous boundary:

- outbox polling/scheduling (`OutboxEventScheduler`, `OutboxEventProcessor`)
- optional Kafka dispatch (`KafkaIntegrationEventDispatcher`)
- retry/recovery logic for transient publication failures

Decision record: `docs/decisions/ADR-002-transactional-outbox-for-reliable-events.md`.

## Failure semantics

- validation/policy failures reject before durable money movement
- runtime failures inside the transaction roll back transfer/account/ledger changes
- outbox publish failures after commit are retried from durable outbox state
- reconciliation identifies post-incident drift and linkage inconsistencies for operator action

## Operational implications

- deployment assumes forward-only schema migration discipline
- backup/restore and migration precheck/postcheck are required controls before risky changes
- outbox lag/failures and reconciliation discrepancies are operational signals, not silent degradations

References:

- `docs/deployment.md`
- `docs/runbook.md`
- `ops/backup/`
- `ops/migration/`

## Evolution path toward service extraction

The current design intentionally keeps strong consistency local. Extraction to separate services is feasible when release cadence and load justify the added complexity.

Expected extraction order (if needed):

1. analytics/consumer concerns first (already async-friendly)
2. reconciliation/reporting workflows
3. high-throughput integration edges

Core transfer + ledger boundary should remain tightly controlled unless a replacement consistency model is in place.

## Supporting code references

- `src/main/java/com/neobank/transfers/service/TransferService.java`
- `src/main/java/com/neobank/ledger/service/LedgerService.java`
- `src/main/java/com/neobank/transfers/service/TransferReversalService.java`
- `src/main/java/com/neobank/outbox/infrastructure/OutboxEventProcessor.java`
- `src/main/java/com/neobank/reconciliation/service/ReconciliationService.java`
- `src/main/java/com/neobank/auth/security/SecurityConfig.java`

