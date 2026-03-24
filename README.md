# Neobank Backend

## 1. Overview

Neobank Backend is a modular-monolith system focused on money movement correctness, ownership enforcement, and operational recoverability.

The repository demonstrates a backend-first architecture where transfer state, ledger records, and domain events are coordinated through explicit transaction boundaries.

## 2. Why this project exists

This project exists to show production-oriented backend engineering decisions in a finance-like domain:

- consistency under concurrent writes
- security controls at API and domain boundaries
- reliable asynchronous integration using transactional outbox
- incident and recovery procedures with executable operational scripts

## 3. What this system demonstrates

- Transactional transfer execution with deterministic account locking (`TransferService`)
- Ledger recording with balancing validation (`LedgerService`)
- Reconciliation checks that detect data drift and linkage inconsistencies (`ReconciliationService`)
- Event publication without dual-write risk (`outbox` module)
- Role and ownership authorization across auth, transfer, ledger, and admin paths

## 4. Core capabilities

- Auth and authorization (`/auth`, role-based and ownership checks)
- Account lifecycle and balance management (`/accounts`)
- Transfers, reversal, and transfer history (`/transfers`)
- Ledger transaction read APIs (`/ledger`)
- Risk evaluation and policy enforcement (`/risk`)
- Admin-triggered reconciliation reports (`/reconciliation`)

## 5. Architecture highlights

- Modular monolith with domain boundaries in `src/main/java/com/neobank/*`
- PostgreSQL as source of truth for transactional state and ledger data
- Flyway migrations as forward-only schema evolution mechanism
- Transactional outbox (`outbox_events`) for asynchronous Kafka publication
- Observability baseline via Actuator, Micrometer, Prometheus, and Grafana

See canonical architecture: `docs/architecture.md`.

## 6. Correctness and reliability guarantees

- Transfer + account balance mutation + ledger recording run in one transactional unit
- Risk rejection prevents transfer completion and does not produce partial money movement
- Ledger transactions are validated for single-currency and balanced debit/credit totals
- Reversal model is append/correct (no destructive rewrite of historical facts)
- Reconciliation detects drift between account balances, transfer terminal states, and ledger linkage

## 7. Technology stack

- Java 21
- Spring Boot 4.0.3 (`spring-boot-starter-parent`)
- Spring Security + JWT
- Spring Data JPA (PostgreSQL)
- Redis (sessions/cache/rate limiting support)
- Kafka + Spring Kafka
- Flyway
- Docker Compose
- Micrometer + Prometheus + Grafana

## 8. Repository structure

```text
src/                    Application and tests
docs/                   Canonical architecture/flow/ADR documentation
docs/portfolio/         Recruiter-oriented summaries (derived from canonical docs)
ops/                    Operational scripts (backup, migration checks, load sanity)
monitoring/             Prometheus and Grafana configuration
compose.yml             Local runtime stack definition
Dockerfile              Application container build
pom.xml                 Build and dependency manifest
```

Historical sprint artifacts are being curated into `docs/archive/` to keep root navigation high-signal.

## 9. Quickstart

```powershell
Copy-Item .env.example .env
.\mvnw.cmd clean verify
docker compose up -d
docker compose ps
```

## 10. API and local runtime

After startup:

- App: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Operational health summary: `http://localhost:8080/actuator/health-summary`
- API docs UI: `http://localhost:8080/swagger-ui/index.html`
- Kafka UI: `http://localhost:8081`

Key API surfaces: `/auth`, `/accounts`, `/transfers`, `/ledger`, `/risk`, `/reconciliation`, `/audit`, `/admin`.

## 11. Documentation map

Canonical engineering docs (source of truth):

- `docs/README.md`
- `docs/architecture.md`
- `docs/auth-flow.md`
- `docs/transfer-flow.md`
- `docs/ledger.md`
- `docs/reversal-flow.md`
- `docs/reconciliation.md`
- `docs/outbox-kafka-flow.md`
- `docs/deployment.md`
- `docs/runbook.md`

Portfolio overlays (derived summaries):

- `docs/portfolio/ARCHITECTURE_ONE_PAGER.md`
- `docs/portfolio/HIRING_PACKET.md`
- `docs/portfolio/DEMO_SCRIPT.md`

## 12. Key architectural decisions

- `docs/decisions/ADR-001-modular-monolith-over-microservices.md`
- `docs/decisions/ADR-002-transactional-outbox-for-reliable-events.md`
- `docs/decisions/ADR-003-double-entry-ledger-and-reversal.md`

## 13. Demo path

1. Read `docs/architecture.md` for module boundaries and data ownership.
2. Run `docker compose up -d` and verify `GET /actuator/health`.
3. Walk a transfer lifecycle using `docs/transfer-flow.md`.
4. Inspect resulting ledger records via `/ledger/transactions/transfer/{transferId}`.
5. Trigger reconciliation via `POST /reconciliation/run` (admin context).
6. Show outbox behavior and failure handling from `docs/outbox-kafka-flow.md` and `ops/` scripts.

## 14. Operational maturity

- Backup and restore scripts: `ops/backup/`
- Migration precheck/postcheck scripts: `ops/migration/`
- Load sanity validation scripts: `ops/load/`
- Monitoring configuration: `monitoring/`
- Operational references:
  - `docs/runbook.md`
  - `docs/deployment.md`
  - `docs/archive/sprints/SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md`
  - `docs/archive/sprints/SPRINT18_MIGRATION_GOVERNANCE.md`
  - `docs/archive/sprints/SPRINT18_ROLLBACK_STRATEGY.md`
  - `docs/archive/sprints/SPRINT18_FAILURE_DRILLS.md`

## 15. Known boundaries

- Single deployable service (modular monolith), not independently deployable domain services.
- Reconciliation is admin-triggered API flow in current implementation, not a built-in scheduled job.
- Kafka publication is optional by configuration (`KAFKA_ENABLED`, `KAFKA_PUBLISH`) and may be disabled in local runs.
- Operational evidence includes historical sprint artifacts; curation is in progress to reduce root noise.
