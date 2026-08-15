# ADR-0006: Redis Cache-Aside Pattern

## Status
Accepted

## Context
Catalog queries (`/api/v1/products/{id}`, `/api/v1/inventory/{productId}`) represent 90%+ of e-commerce traffic. Querying PostgreSQL on every product view creates database I/O bottlenecks.

## Decision
Implement Redis Cache-Aside in `inventory-service` using key spaces `product:{id}` and `inventory:{productId}` with a 1-hour TTL. Cache is evicted upon product update or stock modification. Micrometer counters (`commerce_cache_hits_total`, `commerce_cache_misses_total`) track effectiveness.

## Consequences
- **Pros**: Sub-millisecond read latency, high throughput, protects PostgreSQL database.
- **Cons**: Minor risk of temporary cache staleness during race conditions (mitigated by explicit eviction).
