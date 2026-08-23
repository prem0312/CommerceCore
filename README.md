# CommerceCore

CommerceCore is an e-commerce microservices and order processing backend built with **Java 21/25**, **Spring Boot 3.4**, **Spring Cloud**, **PostgreSQL 16**, **Redis 7.2**, and **Apache Kafka (KRaft mode)**.

It demonstrates core distributed systems patterns including database-per-service, distributed idempotency, transactional outbox for event publishing, optimistic concurrency control, cache-aside caching, and consumer-side event deduplication.

---

## System Architecture

```
                                  +---------------------+
                                  |    Client / HTTP    |
                                  +----------+----------+
                                             |
                                             v
                                  +---------------------+
                                  |  api-gateway (:8080)|
                                  +----------+----------+
                                             |
     +-----------------+---------------------+-----------------+-----------------+
     |                 |                     |                 |                 |
     v                 v                     v                 v                 v
+---------+       +---------+           +---------+       +---------+       +---------+
| auth    |       | user    |           |inventory|       | order   |       | review  |
| :8081   |       | :8082   |           | :8083   |       | :8084   |       | :8087   |
+----+----+       +----+----+           +----+----+       +----+----+       +----+----+
     |                 |                     |                 |                 |
 [Auth DB]         [User DB]          [Inventory DB]       [Order DB]        [Review DB]
                                             |                 |
                                       [Redis Cache]     [Redis Idemp]
                                                               | (Sync REST)
                                                               v
                                                          +---------+
                                                          | payment |
                                                          | :8085   |
                                                          +----+----+
                                                               | [Payment DB]
                                                               | (Outbox)
                                                               v
                                                     +--------------------+
                                                     | Kafka (KRaft Mode) | (:29092)
                                                     +---------+----------+
                                                               |
                                                               v
                                                     +--------------------+
                                                     |notification (:8086)|
                                                     +---------+----------+
                                                               | [Notification DB]
```

---

## Architecture & Technical Patterns

1. **Stateless JWT Authentication & RBAC**:
   - Public registration assigns `USER` role; clients cannot self-promote to `ADMIN`.
   - Initial `ADMIN` account is bootstrapped at startup via environment variables.
   - Spring Cloud Gateway extracts and validates JWT tokens statelessly, forwarding identity headers (`X-User-Id`, `X-User-Role`, `X-User-Email`) downstream.

2. **Database per Service**:
   - Each microservice owns an isolated PostgreSQL database schema.
   - Schema versioning and migrations are managed automatically using Flyway.

3. **Distributed Idempotency**:
   - Two-phase idempotency mechanism on state-changing endpoints (`/api/v1/orders`, `/api/v1/payments`).
   - Fast-path check via Redis (`SET NX` with TTL) coupled with durable PostgreSQL `idempotency_records` table.
   - Replays identical requests safely with HTTP `200 OK` and `Idempotent-Replay: true` header without re-executing business logic.

4. **Transactional Outbox Pattern**:
   - Domain mutations and event records (`outbox_events`) are committed within a single PostgreSQL transaction.
   - A background publisher polls pending outbox records using `SELECT ... FOR UPDATE SKIP LOCKED` and publishes them to Kafka with exponential backoff retries.

5. **Optimistic Locking**:
   - Inventory stock and order entities use JPA `@Version` columns to detect and prevent lost update anomalies during concurrent checkout attempts.

6. **Event Deduplication (Consumer Side)**:
   - Kafka event consumers track processed message IDs in a `processed_events` table with unique constraint `(event_id, consumer_name)` to ensure idempotency across message redeliveries.

---

## Services & Ports

| Service | Port | Description |
| :--- | :--- | :--- |
| **`eureka-service`** | `8700` | Netflix Eureka Service Discovery |
| **`api-gateway`** | `8080` | Spring Cloud Gateway (Routing & JWT Security) |
| **`auth-service`** | `8081` | User registration, authentication, and JWT token issuance |
| **`user-service`** | `8082` | User profiles and address management |
| **`inventory-service`** | `8083` | Product catalog, stock reservations, Redis Cache-Aside |
| **`order-service`** | `8084` | Order placement, state machine, outbox event publishing |
| **`payment-service`** | `8085` | Payment gateway simulation, idempotency, transactional outbox |
| **`notification-service`** | `8086` | Notification dispatcher (simulated multi-channel delivery), Kafka consumers |
| **`review-service`** | `8087` | Product ratings and reviews summary aggregation |

---

## Getting Started

### Prerequisites

* **Java 21 LTS or 25 LTS**
* **Maven 3.9+**
* **PostgreSQL 16**
* **Redis / Memurai**
* **Apache Kafka 3.6+ (KRaft mode)**

### 1. Environment Configuration

Copy the example environment file and configure your local credentials:
```powershell
Copy-Item .env.example .env
```

**Required Environment Variables:**
* `JWT_SECRET` (**Required**): HMAC-SHA256 secret key (minimum 32 characters / 256 bits). If unset or shorter than 32 characters, `auth-service` and `api-gateway` fail startup with a configuration error.
* `COMMERCECORE_ADMIN_PASSWORD` (**Required**): Password for the seeded bootstrap administrator account. If unset, `auth-service` fails startup.
* `COMMERCECORE_ADMIN_EMAIL` (*Optional*): Email for the seeded administrator account (defaults to `admin@commercecore.io`).

### 2. Build the Project and Run Tests

Run the full test suite across all modules:
```powershell
mvn clean verify
```

### 3. Start Native Windows Infrastructure

Set up databases and start native infrastructure (PostgreSQL, Redis, Kafka KRaft):
```powershell
.\scripts\setup-native-infra.ps1
.\scripts\start-infra.ps1
```

### 4. Start Local Spring Boot Microservices

Start all 9 services natively in dependency order:
```powershell
.\scripts\start-local.ps1
```

### 5. Run the End-to-End Smoke Tests

Execute the 11-step end-to-end smoke test suite:
```powershell
.\scripts\smoke-test.ps1
```

### 6. Stop Local Services & Infrastructure

```powershell
.\scripts\stop-local.ps1
.\scripts\stop-infra.ps1
```

---

## Monitoring and Tooling

* **Eureka Dashboard**: [http://localhost:8700](http://localhost:8700)
* **API Gateway Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
* **Swagger / OpenAPI Documentation**:
  - Inventory Service: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)
  - Order Service: [http://localhost:8084/swagger-ui.html](http://localhost:8084/swagger-ui.html)
  - Payment Service: [http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html)
  - Review Service: [http://localhost:8087/swagger-ui.html](http://localhost:8087/swagger-ui.html)

---

## Architecture Decision Records (ADRs)

Detailed architectural justifications are available in the [`docs/decisions/`](docs/decisions/) directory:
* [ADR-0001: Microservices Decomposition](docs/decisions/ADR-0001-microservices-decomposition.md)
* [ADR-0002: Database Per Service Pattern](docs/decisions/ADR-0002-database-per-service.md)
* [ADR-0003: Netflix Eureka Service Discovery](docs/decisions/ADR-0003-eureka-service-discovery.md)
* [ADR-0004: API Gateway Routing & Security](docs/decisions/ADR-0004-api-gateway-routing-security.md)
* [ADR-0005: Stateless JWT Authentication](docs/decisions/ADR-0005-jwt-stateless-authentication.md)
* [ADR-0006: Redis Cache-Aside Pattern](docs/decisions/ADR-0006-redis-cache-aside.md)
* [ADR-0007: Kafka Event-Driven Messaging](docs/decisions/ADR-0007-kafka-event-driven-messaging.md)
* [ADR-0008: Transactional Outbox Pattern](docs/decisions/ADR-0008-transactional-outbox-pattern.md)
* [ADR-0009: Distributed Idempotency Guard](docs/decisions/ADR-0009-distributed-idempotency.md)
* [ADR-0010: Optimistic Locking with @Version](docs/decisions/ADR-0010-optimistic-locking.md)
* [ADR-0011: Observability Stack](docs/decisions/ADR-0011-observability-stack.md)

---

## License

This project is licensed under the MIT License.
