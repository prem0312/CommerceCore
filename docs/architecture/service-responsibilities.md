# Service Responsibilities & Boundaries

| Service Name | Port | Database | Responsibilities |
| :--- | :--- | :--- | :--- |
| **eureka-service** | 8761 | None | Service discovery and dynamic heartbeat registration. |
| **api-gateway** | 8080 | None | Public entrypoint, JWT token authentication, route forwarding, correlation ID generation (`X-Correlation-Id`). |
| **auth-service** | 8081 | `commercecore_auth` | User registration, credential verification (BCrypt 12 rounds), JWT issuance and signature validation. |
| **user-service** | 8082 | `commercecore_user` | User profiles, multiple shipping/billing addresses, admin status toggles, RBAC authorization. |
| **inventory-service** | 8083 | `commercecore_inventory` | Product catalog CRUD, stock reservation, stock release, optimistic locking concurrency control (`@Version`), Redis Cache-Aside. |
| **order-service** | 8084 | `commercecore_order` | Order lifecycle state machine, distributed idempotency, synchronous stock reservation coordination, atomic Outbox persistence, synchronous payment invocation, payment Kafka consumer. |
| **payment-service** | 8085 | `commercecore_payment` | Payment processing simulation, idempotency guard, atomic Outbox commit, Kafka event publishing (`commercecore.payments.v1`). |
| **notification-service**| 8086 | `commercecore_notification` | Multi-channel notification simulation (EMAIL, SMS, IN_APP), Kafka event consumers with `processed_events` deduplication. |
| **review-service** | 8087 | `commercecore_review` | Product reviews and ratings (1-5), duplicate review prevention per user/product, rating summary aggregation, RBAC deletion. |
