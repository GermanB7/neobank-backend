# Neobank Backend - Architecture One-Pager

This document is a hiring-oriented summary. Canonical source: `docs/architecture.md`.

## 1) System Purpose and Scope

Neobank Backend is a modular-monolith financial backend focused on secure account access, atomic transfers, double-entry ledger consistency, asynchronous event publication, and production operations.

### In scope
- Authentication and role-based authorization
- Account ownership and access control
- Atomic money transfers with ledger entries
- Reversal and reconciliation workflows
- Outbox pattern with Kafka dispatch
- Operational hardening (backup/restore, migration governance, incident response)

### Out of scope
- Frontend/UI focus
- Multi-region active-active deployment
- Full card/payments network integrations

## 2) Architecture Snapshot

### Style
- Modular monolith (single deployable app, clear domain boundaries)

### Main components
- Auth and security (`SecurityConfig`, `JwtAuthFilter`)
- Accounts and ownership checks
- Transfer orchestration (`TransferService`)
- Ledger persistence and auditability
- Risk checks and reconciliation
- Domain event to outbox persistence
- Outbox dispatcher (`OutboxEventProcessor`) to Kafka

### Data and infrastructure
- PostgreSQL for transactional source of truth
- Redis for caching/rate-related support
- Kafka for async integration workflows
- Flyway for forward-only schema migrations
- Docker Compose for local/staging-like runtime

## 3) Three Critical Flows

### A) Auth and authorization flow
1. Client authenticates and receives JWT.
2. `JwtAuthFilter` validates token and builds security context.
3. `SecurityConfig` and controller-level rules (including `@PreAuthorize`) enforce role and endpoint restrictions.
4. Domain-level ownership checks protect account resources.

### B) Atomic transfer and ledger flow
1. Transfer request enters transfer API.
2. `TransferService` validates ownership, balance, and risk constraints.
3. Debit/credit state changes and ledger records are persisted in one transactional boundary.
4. Transfer result is returned only after durable commit.

### C) Outbox and Kafka flow
1. Domain action commits business data and outbox record in the same database transaction.
2. `OutboxEventProcessor` reads pending outbox rows.
3. Event is published to Kafka.
4. Success/failure status is tracked, with retry/recovery procedures in operations runbook.

## 4) Correctness Guarantees

- Transactional consistency for transfer + ledger writes
- Double-entry-style bookkeeping model to support auditable money movement
- Reversal/compensation path for controlled correction
- Reconciliation capabilities to detect and resolve drift
- Idempotency protections in async/event processing path

## 5) Reliability and Failure Handling

- Outbox retry path for transient broker/network failures
- Failed-event inspection and controlled requeue process
- Migration precheck/postcheck gates
- Backup + checksum-verified restore playbook
- Severity-based incident response with escalation path

Reference: `docs/runbook.md`

## 6) Security Posture

- JWT-based authentication, stateless request authorization
- Role-based restrictions on sensitive endpoints (admin-only operations)
- Ownership checks to prevent cross-account data access
- Secrets externalization and environment-based configuration

## 7) Operability Signals

- Health/readiness endpoints
- Prometheus metrics and alert rules
- Load sanity scripts for baseline performance regression detection
- Runbook-driven deployment and rollback decision framework

## 8) Key Trade-offs

1. **Modular monolith vs microservices:** faster delivery and transactional simplicity now, with explicit domain boundaries for future extraction.
2. **Synchronous core writes + asynchronous integrations:** keeps money movement deterministic while still supporting event-driven integrations.
3. **Forward-only migrations:** safer production schema evolution with operational discipline over convenience.

## 9) Evidence Index

### Core code references
- `src/main/java/com/neobank/transfers/service/TransferService.java`
- `src/main/java/com/neobank/outbox/infrastructure/OutboxEventProcessor.java`
- `src/main/java/com/neobank/auth/security/SecurityConfig.java`
- `src/main/java/com/neobank/auth/security/JwtAuthFilter.java`

### Migration references
- `src/main/resources/db/migration/V3__transfers.sql`
- `src/main/resources/db/migration/V4__ledger.sql`
- `src/main/resources/db/migration/V8__transfer_reversals.sql`
- `src/main/resources/db/migration/V11__outbox_events.sql`

### Test references
- `src/test/java/com/neobank/transfers/api/TransfersIntegrationTest.java`
- `src/test/java/com/neobank/auth/api/AuthorizationIntegrationTest.java`
- `src/test/java/com/neobank/outbox/infrastructure/OutboxPatternIntegrationTest.java`
- `src/test/java/com/neobank/reconciliation/api/ReconciliationIntegrationTest.java`

### Operations references
- `docs/runbook.md`
- `docs/deployment.md`
- `docs/archive/sprints/SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md`
- `docs/archive/sprints/SPRINT18_MIGRATION_GOVERNANCE.md`
- `ops/QUICK_REFERENCE.md`

