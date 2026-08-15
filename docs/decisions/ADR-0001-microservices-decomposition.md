# ADR-0001: Microservices Decomposition

## Status
Accepted

## Context
E-commerce domains have differing scaling requirements, release cadences, and fault-domain boundaries. For example, product browsing is read-heavy and latency-sensitive, while order placement requires strict transactional consistency and distributed idempotency.

## Decision
Decompose CommerceCore into 7 business microservices (`auth`, `user`, `inventory`, `order`, `payment`, `notification`, `review`) coordinated through an edge gateway and Netflix Eureka service discovery.

## Consequences
- **Pros**: Independent scaling, isolated deployments, decoupled data models, clear team boundaries.
- **Cons**: Distributed systems complexity, eventual consistency overhead, network latency between services.
