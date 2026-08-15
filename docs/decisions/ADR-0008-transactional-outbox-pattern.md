# ADR-0008: Transactional Outbox Pattern with SKIP LOCKED

## Status
Accepted

## Context
Writing to a database and sending a Kafka message in two separate operations introduces dual-write anomalies (e.g. DB commits, but Kafka fails; or Kafka sends, but DB transaction rolls back).

## Decision
Implement the Transactional Outbox pattern. Domain state and `outbox_events` are inserted in the exact same local PostgreSQL transaction. A background publisher polls using `SELECT * FROM outbox_events WHERE status = 'PENDING' ... FOR UPDATE SKIP LOCKED` and publishes to Kafka with exponential backoff retries.

## Consequences
- **Pros**: 100% atomicity between database state and published events without 2-phase commit (2PC) or XA transactions.
- **Cons**: Small polling latency before event publication (mitigated by 2-second polling intervals).
