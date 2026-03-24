# Auth and Authorization Flow

## Objective
Ensure only authenticated and authorized users can access account and transfer operations, with explicit admin boundaries.

## Flow
1. Client sends credentials and receives JWT.
2. `JwtAuthFilter` validates token and creates authentication context.
3. `SecurityConfig` applies endpoint security policy.
4. Controller-level `@PreAuthorize` enforces role restrictions.
5. Domain-level ownership checks enforce account-level access control.

## Evidence
- `src/main/java/com/neobank/auth/security/JwtAuthFilter.java`
- `src/main/java/com/neobank/auth/security/SecurityConfig.java`
- `src/main/java/com/neobank/transfers/api/controller/TransferController.java`
- `src/test/java/com/neobank/auth/api/AuthorizationIntegrationTest.java`

## Security trade-offs
- JWT keeps runtime stateless and simple to scale.
- Authorization is layered (filter, endpoint, domain checks) to reduce single-point bypass risk.
- Advanced features (fine-grained ABAC, policy engine) are intentionally out of scope.

