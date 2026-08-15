# API Design & Contracts

All external requests enter through the API Gateway at `http://localhost:8080`.

## 1. Authentication Service (`/api/v1/auth`)

- `POST /api/v1/auth/register`
  - Body: `{ "name": "Alice Smith", "email": "alice@example.com", "password": "Password123!" }` (Public registration strictly assigns `USER` role; client cannot select `role`)
  - Response: `201 Created` with AuthResponse (`accessToken`, `tokenType`, `expiresIn`, `userId`, `email`, `role`, `name`)
- `POST /api/v1/auth/login`
  - Body: `{ "email": "alice@example.com", "password": "Password123!" }`
  - Response: `200 OK` with AuthResponse
  - *Default Seeded Admin for Development*: `admin@commercecore.io` / `AdminPass123!` (role: `ADMIN`)
- `GET /api/v1/auth/validate`
  - Header: `Authorization: Bearer <token>`
  - Response: `200 OK` with `{ "valid": true, "userId": 1, "email": "alice@example.com", "role": "USER" }`

## 2. User Service (`/api/v1/users`)

- `GET /api/v1/users/me`
  - Headers: `Authorization: Bearer <token>` (Gateway injects `X-User-Id`, `X-User-Role`)
  - Response: `200 OK` with UserProfileResponse
- `GET /api/v1/users/{id}`
  - Response: `200 OK` with UserProfileResponse
- `PUT /api/v1/users/{id}`
  - Body: `{ "firstName": "Alice", "lastName": "Johnson", "phoneNumber": "+1234567890" }`
  - Response: `200 OK` with updated profile
- `POST /api/v1/users/{id}/addresses`
  - Body: `{ "street": "123 Main St", "city": "Seattle", "state": "WA", "postalCode": "98101", "country": "USA", "defaultShipping": true, "defaultBilling": true }`
  - Response: `201 Created` with AddressResponse

## 3. Inventory & Product Service (`/api/v1/products`, `/api/v1/inventory`)

- `GET /api/v1/products` (supports `?page=0&size=10&sort=name,asc`)
- `GET /api/v1/products/{id}` (Cached in Redis: `product:{id}`)
- `POST /api/v1/products` (Requires ADMIN role)
  - Body: `{ "sku": "SKU-PROD-001", "name": "Mechanical Keyboard", "description": "RGB mechanical keyboard", "price": 129.99, "initialQuantity": 100 }`
  - Response: `201 Created` with ProductResponse
- `GET /api/v1/inventory/{productId}` (Cached in Redis: `inventory:{productId}`)
- `POST /api/v1/inventory/reserve`
  - Body: `{ "productId": 1, "quantity": 2 }`
  - Response: `200 OK` with updated InventoryResponse
- `POST /api/v1/inventory/batch-reserve`
  - Body: `{ "items": [{ "productId": 1, "quantity": 2 }] }`
  - Response: `200 OK`

## 4. Order Service (`/api/v1/orders`)

- `POST /api/v1/orders`
  - Headers: `Authorization: Bearer <token>`, `Idempotency-Key: <uuid-key>`
  - Body: `{ "items": [{ "productId": 1, "quantity": 2, "unitPrice": 129.99 }], "currency": "USD" }`
  - Response (first call): `201 Created` with OrderResponse
  - Response (repeated call with same key): `200 OK` with `Idempotent-Replay: true` header
- `GET /api/v1/orders/{id}`
- `GET /api/v1/orders/user/{userId}`
- `POST /api/v1/orders/{id}/cancel`

## 5. Payment Service (`/api/v1/payments`)

- `POST /api/v1/payments`
  - Headers: `Idempotency-Key: <key>`
  - Body: `{ "orderId": 1, "amount": 259.98, "currency": "USD" }`
  - Response: `201 Created` with PaymentResponse (`status`: SUCCESS / FAILED)
- `GET /api/v1/payments/{id}`
- `GET /api/v1/payments/order/{orderId}`

## 6. Notification Service (`/api/v1/notifications`)

- `GET /api/v1/notifications/user/{userId}` (Paginated audit log of dispatched notifications)
- `GET /api/v1/notifications/user/{userId}/all`

## 7. Review Service (`/api/v1/reviews`)

- `POST /api/v1/reviews`
  - Body: `{ "productId": 1, "rating": 5, "comment": "Excellent keyboard!" }`
  - Response: `201 Created` with ReviewResponse
- `GET /api/v1/reviews/product/{productId}`
- `GET /api/v1/reviews/product/{productId}/summary`
  - Response: `{ "productId": 1, "averageRating": 4.8, "totalReviews": 12, "ratingDistribution": { "5": 10, "4": 2, "3": 0, "2": 0, "1": 0 } }`
- `DELETE /api/v1/reviews/{id}`
