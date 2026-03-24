# Neobank Backend - Hiring Packet

This document provides a fast, high-signal reading path for backend interviewers.

## 1) 15-Minute Evaluator Path

1. Read `docs/architecture.md` (canonical system model and trade-offs)
2. Read `docs/portfolio/ARCHITECTURE_ONE_PAGER.md` (compressed summary)
3. Read `docs/portfolio/DEMO_SCRIPT.md` (what to verify live)
4. Review transaction and event reliability evidence:
   - `src/test/java/com/neobank/transfers/api/TransfersIntegrationTest.java`
   - `src/test/java/com/neobank/outbox/infrastructure/OutboxPatternIntegrationTest.java`
5. Review operational maturity:
   - `docs/runbook.md`
   - `docs/deployment.md`

## 2) What This Project Demonstrates

- Transactional correctness in a finance-like domain
- Security-aware API design with authn/authz and ownership controls
- Event-driven reliability with outbox + Kafka
- Production-oriented operations and failure recovery discipline
- Architectural decision-making with explicit trade-offs

## 3) Recommended Deep-Dive Questions

1. Why modular monolith before microservices?
2. How do you guarantee transfer and ledger consistency?
3. How do you prevent event loss/duplication in outbox dispatch?
4. What is your rollback strategy when migrations fail?
5. How do you choose rollback vs forward-fix during incidents?

## 4) Decision Records

- `docs/decisions/ADR-001-modular-monolith-over-microservices.md`
- `docs/decisions/ADR-002-transactional-outbox-for-reliable-events.md`
- `docs/decisions/ADR-003-double-entry-ledger-and-reversal.md`
- `docs/decisions/ADR-004-ownership-enforcement-boundary.md`
- `docs/decisions/ADR-005-forward-only-flyway-migrations.md`
- `docs/decisions/ADR-006-operational-recovery-model.md`

## 5) Known Gaps and Honest Boundaries

- No frontend product surface; backend platform focus by design
- No active-active multi-region architecture
- No exactly-once global guarantee across all external consumers

These are intentional constraints for scope control and portfolio signal concentration.

## 6) Interview Walkthrough Recommendation

Use `docs/portfolio/DEMO_SCRIPT.md` for a 7-minute structured walkthrough:
- 2 min architecture
- 3 min critical flows (auth, transfer, outbox)
- 2 min operations and failure handling

