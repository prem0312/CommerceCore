# ADR-0009: Dual-Layer Distributed Idempotency (Redis + PostgreSQL)

## Status
Accepted

## Context
Network timeouts and client retries during order placement or payment processing can result in duplicate orders, double-charging customers, and over-reserving inventory.

## Decision
Implement a dual-layer distributed idempotency engine:
1. Fast in-memory check via Redis `SET NX` with 5-minute lock TTL.
2. Durable persistence in PostgreSQL `idempotency_records` table with unique constraint `(user_id, idempotency_key)` and SHA-256 request payload hash.
3. Edge case handling:
   - Identical key + completed: returns cached response with `Idempotent-Replay: true` header.
   - Identical key + concurrent execution: returns `409 Conflict` (`REQUEST_IN_PROGRESS`).
   - Identical key + mismatched payload hash: returns `409 Conflict` (`IDEMPOTENCY_PAYLOAD_MISMATCH`).
   - Redis outage: gracefully falls back directly to PostgreSQL durable unique constraint.

## Consequences
- **Pros**: Protects critical financial transactions against duplicate execution and payload tampering.
- **Cons**: Small storage overhead for idempotency record tables.
