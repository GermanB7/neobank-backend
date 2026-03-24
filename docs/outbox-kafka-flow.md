# Outbox + Kafka Flow

## Objective
Guarantee reliable event publication from transactional state changes without dual-write inconsistency.

## Flow
1. Business transaction commits domain changes and outbox row together.
2. `OutboxEventProcessor` polls pending outbox rows.
3. Event is published to Kafka.
4. Outbox status is updated to success/failure.
5. Failed events are retried and recoverable through operations runbook.

## Failure model
- Kafka unavailable: events remain persisted in outbox and are retried later.
- Temporary app failure: processing resumes from persisted outbox state.
- Persistent publish failures: events marked failed, investigated, and requeued after root-cause fix.

## Operational controls
- Pending/failure/age metrics monitored
- Manual requeue procedure documented
- Incident escalation path for backlog growth

## Evidence
- `src/main/resources/db/migration/V11__outbox_events.sql`
- `src/main/java/com/neobank/outbox/infrastructure/OutboxEventProcessor.java`
- `src/test/java/com/neobank/outbox/infrastructure/OutboxPatternIntegrationTest.java`
- `docs/runbook.md`
- `docs/archive/sprints/SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md`
- `docs/decisions/ADR-002-transactional-outbox-for-reliable-events.md`

