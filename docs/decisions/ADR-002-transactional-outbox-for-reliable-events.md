# ADR-002: Transactional Outbox for Reliable Event Publication

- Status: Accepted
- Date: 2026-03-19

## Context

Business changes (transfers, reconciliations, risk outcomes) may need async downstream processing. Direct publish to Kafka inside request flow risks dual-write inconsistency: DB commit may succeed while Kafka publish fails, or vice versa.

## Decision

Use transactional outbox:
1. Persist business state and outbox event in one database transaction.
2. Process outbox events asynchronously with retry logic.
3. Publish to Kafka and update outbox status.

## Consequences

### Positive
- Eliminates dual-write inconsistency in core write path
- Improves resilience against transient broker/network failures
- Supports operational recovery and replay controls

### Negative
- Adds operational responsibility (monitoring backlog/failures)
- Adds eventual consistency for downstream consumers

## Mitigations

- Monitor pending/failed/age metrics
- Define manual requeue runbook after root-cause correction
- Keep consumer handlers idempotent where possible

## Evidence

- `src/main/resources/db/migration/V11__outbox_events.sql`
- `src/main/java/com/neobank/outbox/infrastructure/OutboxEventProcessor.java`
- `src/test/java/com/neobank/outbox/infrastructure/OutboxPatternIntegrationTest.java`
- `docs/runbook.md`
- `docs/archive/sprints/SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md`

