# ADR-004: Ownership Enforcement at Domain-Service Boundary

- Status: Accepted
- Date: 2026-03-19

## Context

Endpoint-level authorization alone is insufficient for financial operations. Ownership must be enforced where business state is mutated, otherwise internal call paths or controller mistakes can bypass account-level access rules.

## Decision

Enforce account ownership in domain services (for example transfer and ledger access checks), while keeping API security as a first gate.

This creates a layered model:

1. API/auth layer validates identity and coarse role policy.
2. Domain service validates resource ownership before sensitive state reads/writes.

## Alternatives considered

1. Controller-only ownership checks.
2. Database row-level security only.
3. External policy engine for all ownership rules.

## Consequences

### Positive

- Reduces bypass risk from endpoint misconfiguration.
- Keeps ownership guarantees close to business invariants.
- Improves testability with service-level integration tests.

### Negative

- Ownership logic can be repeated across services if not standardized.
- Requires discipline to keep checks consistent in new write paths.

## Evidence

- `src/main/java/com/neobank/transfers/service/TransferService.java`
- `src/main/java/com/neobank/ledger/service/LedgerService.java`
- `src/main/java/com/neobank/auth/security/SecurityConfig.java`
- `src/test/java/com/neobank/auth/api/AuthorizationIntegrationTest.java`

