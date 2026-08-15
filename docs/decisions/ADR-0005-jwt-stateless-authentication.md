# ADR-0005: Stateless JWT Authentication and Role-Based Access Control

## Status
Accepted

## Context
Stateful session stores (e.g. server sessions) complicate horizontal microservice scaling and introduce cross-region synchronization latency.

## Decision
Utilize cryptographically signed JSON Web Tokens (JJWT / HMAC-SHA256) issued by `auth-service`. Roles (`USER`, `ADMIN`) are encoded in the JWT claims. Downstream services read forwarded HTTP headers (`X-User-Role`) injected by the gateway after signature verification.

## Consequences
- **Pros**: 100% stateless, zero auth database calls on subsequent requests, high throughput.
- **Cons**: Token revocation before expiration requires explicit blocklisting if needed.
