# CommerceCore - System Architecture

## Architecture Overview

CommerceCore is an e-commerce microservices platform built using **Java 21/25**, **Spring Boot 3.4**, **Spring Cloud**, **Apache Kafka (KRaft mode)**, **Redis 7.2**, and **PostgreSQL 16**.

```
                           +------------------------+
                           |      Client / UI       |
                           +-----------+------------+
                                       |
                                       | HTTP / REST (JWT in Authorization header)
                                       v
                           +------------------------+
                           |  Spring Cloud Gateway  |  (:8080)
                           | (Auth validation /     |
                           |  Correlation ID injection)
                           +-----------+------------+
                                       |
    +----------------------------------+----------------------------------+
    |                  |               |                |                 |
    v                  v               v                v                 v
+--------+        +--------+      +---------+      +---------+       +---------+
| Auth   |        | User   |      |Inventory|      | Order   |       | Review  |
| Service|        | Service|      | Service |      | Service |       | Service |
| (:8081)|        | (:8082)|      | (:8083) |      | (:8084) |       | (:8087) |
+----+---+        +----+---+      +----+----+      +----+----+       +----+----+
     |                 |               |                |                 |
     | DB              | DB            | DB + Redis     | DB + Redis      | DB
     v                 v               v                v                 v
[Auth DB]         [User DB]      [Inventory DB]    [Order DB]        [Review DB]
                                                        |
                                                        | Sync HTTP (Inventory & Payment)
                                                        v
                                                   +---------+
                                                   | Payment |
                                                   | Service | (:8085)
                                                   +----+----+
                                                        | DB + Redis
                                                        v
                                                   [Payment DB]
                                                        |
                                                        | Transactional Outbox
                                                        v
                                             +--------------------+
                                             | Kafka KRaft Cluster| (:9092)
                                             +----------+---------+
                                                        |
                                                        | Async Event Consumption
                                                        v
                                             +--------------------+
                                             |Notification Service| (:8086)
                                             +----------+---------+
                                                        | DB
                                                        v
                                               [Notification DB]
```

## Key Architectural Highlights

1. **Service Discovery**: Netflix Eureka registry running on port `8761`. Microservices register dynamically and resolve upstream/downstream instances.
2. **Edge Security**: Spring Cloud Gateway intercepts public traffic, validates JWT signatures statelessly, extracts claims, and forwards identity headers (`X-User-Id`, `X-User-Email`, `X-User-Role`) down to downstream services.
3. **Database per Service**: Strict isolation. Seven independent PostgreSQL databases. Zero cross-service foreign keys or table joins.
4. **Optimistic Locking**: Hibernate `@Version` on critical entities (`Inventory`, `Order`) protects against lost updates under extreme concurrent order load without blocking read traffic.
5. **Transactional Outbox**: Events (`OrderCreated`, `PaymentCompleted`, etc.) are written to `outbox_events` in the exact same DB transaction as domain entity state changes. Polling publisher with `SELECT ... FOR UPDATE SKIP LOCKED` guarantees reliable at-least-once Kafka publication with zero distributed 2PC transaction overhead.
6. **Distributed Idempotency**: Dual-layer idempotency guard. Fast Redis coordination (`SET NX` with TTL) backed by a durable PostgreSQL `idempotency_records` table with unique constraints. Detects duplicate submissions, returns cached responses with `Idempotent-Replay: true`, and returns HTTP 409 Conflict if payload data has changed.
7. **Consumer Deduplication**: Kafka listeners maintain a `processed_events` table with unique constraint `(event_id, consumer_name)` preventing duplicate side-effects.
