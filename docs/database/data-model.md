# Database Data Model

CommerceCore strictly enforces **Database per Service**. Each service manages its own PostgreSQL schema independently via Flyway migrations.

```
+---------------------------------------------------------------------------------------------------+
|                                      POSTGRESQL INSTANCE                                          |
|                                                                                                   |
|  [commercecore_auth]                                 [commercecore_user]                         |
|  - users (id, email, password_hash, role, status)    - user_profiles (id, user_id, email, names) |
|                                                      - addresses (id, profile_id, street, city)   |
|                                                                                                   |
|  [commercecore_inventory]                            [commercecore_order]                        |
|  - products (id, sku, name, price, is_active)        - orders (id, user_id, status, total, ver)  |
|  - inventory (id, product_id, available, res, ver)   - order_items (id, order_id, prod_id, qty)  |
|                                                      - idempotency_records (id, key, hash, st)    |
|                                                      - outbox_events (id, topic, payload, st)     |
|                                                      - processed_events (id, evt_id, consumer)    |
|                                                                                                   |
|  [commercecore_payment]                              [commercecore_notification]                 |
|  - payments (id, order_id, txn_id, amount, status)   - notifications (id, user_id, ch, msg, st)  |
|  - idempotency_records (id, key, hash, status)       - processed_events (id, evt_id, consumer)    |
|  - outbox_events (id, topic, payload, status)                                                     |
|  - processed_events (id, evt_id, consumer)                                                        |
|                                                                                                   |
|  [commercecore_review]                                                                            |
|  - reviews (id, user_id, product_id, rating, comment)|                                            |
+---------------------------------------------------------------------------------------------------+
```

## Concurrency and Integrity Rules

1. **Optimistic Locking**:
   - `inventory` table has column `version BIGINT NOT NULL DEFAULT 0`.
   - `orders` table has column `version BIGINT NOT NULL DEFAULT 0`.
   - Prevents race conditions during simultaneous stock reservations.
2. **Idempotency Guard**:
   - `idempotency_records` table has `CONSTRAINT uk_* UNIQUE (user_id, idempotency_key)`.
3. **Consumer Event Deduplication**:
   - `processed_events` table has `CONSTRAINT uk_* UNIQUE (event_id, consumer_name)`.
4. **Outbox Polling Concurrency**:
   - Polling workers use `SELECT * FROM outbox_events ... FOR UPDATE SKIP LOCKED` to allow multiple concurrent service replicas to publish outbox rows without row lock contention.
