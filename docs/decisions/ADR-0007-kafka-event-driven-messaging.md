# ADR-0007: Kafka Event-Driven Messaging (KRaft Mode)

## Status
Accepted

## Context
Synchronous REST cascades across microservices cause cascading failures, high tail latency, and tight operational coupling.

## Decision
Adopt Apache Kafka in KRaft mode (ZooKeeper-less) for asynchronous event choreography (`commercecore.orders.v1`, `commercecore.payments.v1`). Event publishers use partition keying by `orderId` to guarantee in-order delivery.

## Consequences
- **Pros**: High-throughput distributed log, resilient asynchronous communication, KRaft simplifies operations.
- **Cons**: Eventual consistency requires careful UI/UX and retry design.
