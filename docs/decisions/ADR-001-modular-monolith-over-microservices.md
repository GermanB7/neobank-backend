# ADR-001: Modular Monolith Over Early Microservices

- Status: Accepted
- Date: 2026-03-19

## Context

The project aims to demonstrate serious backend engineering depth with strong transactional correctness in a finance-like domain. Early microservices would increase distributed complexity (cross-service transactions, deployment overhead, contract drift) before domain boundaries are fully stabilized.

## Decision

Adopt a modular-monolith architecture with explicit domain module boundaries inside a single deployable Spring Boot application.

## Consequences

### Positive
- Strong transactional consistency for transfer and ledger operations
- Faster development and refactoring speed while domain matures
- Simpler local and CI environments

### Negative
- Independent scaling/deployment by domain is limited
- Requires discipline to avoid tight coupling between modules

## Mitigations

- Keep package boundaries explicit by domain
- Use clear API/service interfaces per module
- Use domain events/outbox to reduce direct coupling and prepare extraction paths

## Revisit Trigger

Revisit when independent release cadence, team scaling, or traffic profile makes per-domain deployment a net benefit.

