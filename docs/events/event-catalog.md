# Kafka Event Catalog

All domain events in CommerceCore are serialized as standardized JSON payloads wrapped in an `EventEnvelope<T>`:

```json
{
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "eventType": "OrderCreated",
  "occurredAt": "2026-08-16T12:00:00.000Z",
  "correlationId": "corr-8f0a213b",
  "aggregateId": "100",
  "payload": { ... }
}
```

## Kafka Topics

### 1. `commercecore.orders.v1`
- **Key**: `orderId` (ensures strict partition ordering per order)
- **Events**:
  - `OrderCreated`: Dispatched when order is initially placed in `PAYMENT_PENDING` state.
  - `OrderConfirmed`: Dispatched when payment is verified and order transitions to `CONFIRMED`.
  - `OrderCancelled`: Dispatched when order is cancelled by user or payment fails.
  - `OrderShipped`: Dispatched when order items ship.

### 2. `commercecore.payments.v1`
- **Key**: `orderId`
- **Events**:
  - `PaymentCompleted`: Dispatched upon successful transaction settlement.
  - `PaymentFailed`: Dispatched upon declined charge, triggering order cancellation and stock release.

## Topic Consumers & Idempotency

| Topic | Consumer Service | Consumer Group | Deduplication Key |
| :--- | :--- | :--- | :--- |
| `commercecore.payments.v1` | `order-service` | `order-service-group` | `(eventId, "order-service-payment-consumer")` |
| `commercecore.orders.v1` | `notification-service` | `notification-service-order-group` | `(eventId, "notification-service-consumer")` |
| `commercecore.payments.v1` | `notification-service` | `notification-service-payment-group` | `(eventId, "notification-service-consumer")` |
