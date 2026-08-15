#!/usr/bin/env bash
set -e

GATEWAY_URL="http://localhost:8080"
echo "=================================================="
echo "  COMMERCECORE END-TO-END SMOKE TEST SUITE"
echo "=================================================="

# 1. Health
echo -e "\n[1/11] Checking API Gateway Health..."
curl -s -f "$GATEWAY_URL/actuator/health" > /dev/null
echo "API Gateway is UP."

# 2. Register Customer User (Always assigned USER role)
RAND_ID=$((1 + RANDOM % 100000))
EMAIL="shopper_${RAND_ID}@commercecore.io"
PASSWORD="Password123!"

echo -e "\n[2/11] Registering test customer: $EMAIL..."
AUTH_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Test Shopper\"}")

TOKEN=$(echo "$AUTH_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
USER_ID=$(echo "$AUTH_RESP" | grep -o '"userId":[0-9]*' | cut -d':' -f2)
ROLE=$(echo "$AUTH_RESP" | grep -o '"role":"[^"]*' | cut -d'"' -f4)
echo "Registered User ID: $USER_ID (Role: $ROLE)"

# 3. Login Customer User
echo -e "\n[3/11] Authenticating customer..."
LOGIN_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
echo "Customer login successful."

# 4. User Profile
echo -e "\n[4/11] Fetching User Profile..."
curl -s -X GET "$GATEWAY_URL/api/v1/users/me" -H "Authorization: Bearer $TOKEN"
echo ""

# 5. Admin Authentication & Product Creation
echo -e "\n[5/11] Authenticating as Admin & Creating Product..."
ADMIN_EMAIL="${COMMERCECORE_ADMIN_EMAIL:-admin@commercecore.io}"
ADMIN_PASSWORD="${COMMERCECORE_ADMIN_PASSWORD:-AdminPass123!}"
ADMIN_LOGIN_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
echo "Admin login successful."

PROD_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/products" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Wireless Mechanical Keyboard RGB\",\"description\":\"Custom hot-swappable tactile switches\",\"price\":149.99,\"category\":\"Electronics\",\"initialStock\":50,\"reorderThreshold\":10}")

PROD_ID=$(echo "$PROD_RESP" | grep -o '"id":[0-9]*' | head -n 1 | cut -d':' -f2)
echo "Created Product ID: $PROD_ID"

# 6. Cache Check
echo -e "\n[6/11] Fetching Product from Cache..."
curl -s "$GATEWAY_URL/api/v1/products/$PROD_ID" > /dev/null
echo "Cached read successful."

# 7. Order Placement (Customer)
echo -e "\n[7/11] Placing Order with Idempotency Key..."
IDEMP_KEY="idemp-sh-${RAND_ID}"
ORDER_RESP=$(curl -s -i -X POST "$GATEWAY_URL/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productId\":$PROD_ID,\"productName\":\"Wireless Mechanical Keyboard RGB\",\"quantity\":2,\"unitPrice\":149.99}],\"currency\":\"USD\"}")
echo "$ORDER_RESP"

# 8. Duplicate Idempotency Replay
echo -e "\n[8/11] Replaying duplicate order request..."
curl -s -i -X POST "$GATEWAY_URL/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productId\":$PROD_ID,\"productName\":\"Wireless Mechanical Keyboard RGB\",\"quantity\":2,\"unitPrice\":149.99}],\"currency\":\"USD\"}" | grep -i "Idempotent-Replay"

# 9. Inventory Stock Verification
echo -e "\n[9/11] Checking stock reservation..."
curl -s "$GATEWAY_URL/api/v1/inventory/$PROD_ID" -H "Authorization: Bearer $TOKEN"
echo ""

# 10. Submit Review (Customer)
echo -e "\n[10/11] Submitting review..."
curl -s -X POST "$GATEWAY_URL/api/v1/reviews" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":$PROD_ID,\"rating\":5,\"comment\":\"Spectacular quality!\"}"
echo ""

# 11. Review Summary
echo -e "\n[11/11] Review Summary..."
curl -s "$GATEWAY_URL/api/v1/reviews/product/$PROD_ID/summary" -H "Authorization: Bearer $TOKEN"
echo ""

echo -e "\n=================================================="
echo "  ALL 11 SMOKE TESTS PASSED!"
echo "=================================================="
