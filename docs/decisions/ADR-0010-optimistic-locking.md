# ADR-0010: Optimistic Locking with Hibernate @Version

## Status
Accepted

## Context
High-demand flash sales cause simultaneous concurrent requests attempting to reserve the same inventory stock. Pessimistic database locking (`SELECT FOR UPDATE`) causes database connection starvation and degraded read throughput.

## Decision
Use optimistic concurrency control via Hibernate `@Version Long version` on `Inventory` and `Order` entities. When a collision occurs, Hibernate throws `OptimisticLockException`, which is caught and retried automatically up to 3 times before failing gracefully.

## Consequences
- **Pros**: Non-blocking reads, zero database row locking bottleneck during browsing, deterministic concurrency protection.
- **Cons**: Write retries under high contention (mitigated with exponential backoff).
