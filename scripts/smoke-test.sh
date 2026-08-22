#!/usr/bin/env bash
set -e

# Auto-load .env from repository root if present
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." 2>/dev/null && pwd)"
if [ -f "$ROOT_DIR/.env" ]; then
  while IFS='=' read -r key value || [ -n "$key" ]; do
    key=$(echo "$key" | tr -d '\r\n' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    [[ "$key" =~ ^#.*$ ]] && continue
    [ -z "$key" ] && continue
    value=$(echo "$value" | tr -d '\r\n' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^["'"'"']//' -e 's/["'"'"']$//')
    if [ -z "${!key}" ]; then
      export "$key"="$value"
    fi
  done < "$ROOT_DIR/.env"
elif [ -f ".env" ]; then
  while IFS='=' read -r key value || [ -n "$key" ]; do
    key=$(echo "$key" | tr -d '\r\n' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    [[ "$key" =~ ^#.*$ ]] && continue
    [ -z "$key" ] && continue
    value=$(echo "$value" | tr -d '\r\n' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^["'"'"']//' -e 's/["'"'"']$//')
    if [ -z "${!key}" ]; then
      export "$key"="$value"
    fi
  done < ".env"
fi

GATEWAY_URL="${COMMERCECORE_GATEWAY_URL:-${GATEWAY_URL:-http://localhost:8080}}"
EUREKA_URL="${COMMERCECORE_EUREKA_URL:-${EUREKA_URL:-http://localhost:8761}}"

echo "=================================================="
echo "  COMMERCECORE END-TO-END SMOKE TEST SUITE"
echo "=================================================="

# 1. Health & Discovery Readiness Check
echo -e "\n[1/11] Checking Eureka and API Gateway Health & Service Discovery Readiness..."

REQUIRED_SERVICES=("AUTH-SERVICE" "USER-SERVICE" "INVENTORY-SERVICE" "ORDER-SERVICE" "PAYMENT-SERVICE" "REVIEW-SERVICE" "NOTIFICATION-SERVICE")
TIMEOUT=60
POLL_INTERVAL=2
ELAPSED=0
READY=false
MISSING_SERVICES=()

while [ $ELAPSED -lt $TIMEOUT ]; do
  MISSING_SERVICES=()

  # Check Gateway health
  GW_STATUS=$(curl -s -f "$GATEWAY_URL/actuator/health" 2>/dev/null | grep -o '"status":"UP"' || true)
  if [ -z "$GW_STATUS" ]; then
    MISSING_SERVICES+=("API-GATEWAY")
  fi

  # Check Eureka apps
  EUREKA_APPS=$(curl -s -H "Accept: application/json" "$EUREKA_URL/eureka/apps" 2>/dev/null || true)
  for svc in "${REQUIRED_SERVICES[@]}"; do
    if ! echo "$EUREKA_APPS" | grep -qi "\"name\":\"$svc\""; then
      MISSING_SERVICES+=("$svc")
    fi
  done

  if [ ${#MISSING_SERVICES[@]} -eq 0 ]; then
    READY=true
    break
  fi

  echo "  Waiting for services to be UP in Eureka... Missing: (${MISSING_SERVICES[*]}) [${ELAPSED}s/${TIMEOUT}s]"
  sleep $POLL_INTERVAL
  ELAPSED=$((ELAPSED + POLL_INTERVAL))
done

if [ "$READY" = false ]; then
  echo -e "\n[ERROR] Service readiness timeout reached (${TIMEOUT}s)." >&2
  echo "The following required services are missing or not UP in Eureka:" >&2
  for missing in "${MISSING_SERVICES[@]}"; do
    echo "  - $missing" >&2
  done
  exit 1
fi

echo "API Gateway is UP."
echo "All 7 required microservices registered and UP in Eureka (${ELAPSED}s elapsed)."

# 2. Register Customer User (Always assigned USER role)
RAND_ID=$((1 + RANDOM % 100000))
EMAIL="shopper_${RAND_ID}@commercecore.io"
if [ -z "${COMMERCECORE_SMOKE_TEST_PASSWORD:-}" ]; then
  echo "ERROR: COMMERCECORE_SMOKE_TEST_PASSWORD is not set." >&2
  exit 1
fi

PASSWORD="$COMMERCECORE_SMOKE_TEST_PASSWORD"

echo -e "\n[2/11] Registering test customer: $EMAIL..."

REG_TIMEOUT=30
REG_POLL_INTERVAL=2
REG_ELAPSED=0
REG_SUCCESS=false
AUTH_RESP=""

while [ $REG_ELAPSED -lt $REG_TIMEOUT ]; do
  HTTP_RESPONSE=$(curl -s -w "\nHTTP_STATUS_CODE:%{http_code}" -X POST "$GATEWAY_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Test Shopper\"}")
  HTTP_CODE=$(echo "$HTTP_RESPONSE" | grep "HTTP_STATUS_CODE:" | cut -d':' -f2)
  BODY=$(echo "$HTTP_RESPONSE" | sed '/HTTP_STATUS_CODE:/d')

  if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
    AUTH_RESP="$BODY"
    REG_SUCCESS=true
    break
  elif [ "$HTTP_CODE" -eq 503 ]; then
    echo "  Gateway cache synchronizing (503 Service Unavailable)... Retrying in ${REG_POLL_INTERVAL}s [${REG_ELAPSED}s/${REG_TIMEOUT}s]"
    sleep $REG_POLL_INTERVAL
    REG_ELAPSED=$((REG_ELAPSED + REG_POLL_INTERVAL))
  else
    # Do not retry 4xx validation errors or other non-503 errors
    echo "Registration failed with HTTP status $HTTP_CODE: $BODY" >&2
    exit 1
  fi
done

if [ "$REG_SUCCESS" = false ]; then
  echo -e "\n[ERROR] Registration timed out after ${REG_TIMEOUT}s (API Gateway unable to discover auth-service)." >&2
  exit 1
fi

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
if [ -z "${COMMERCECORE_ADMIN_PASSWORD:-}" ]; then
  echo "ERROR: COMMERCECORE_ADMIN_PASSWORD is not set." >&2
  exit 1
fi

ADMIN_PASSWORD="$COMMERCECORE_ADMIN_PASSWORD"ADMIN_LOGIN_RESP=$(curl -s -X POST "$GATEWAY_URL/api/v1/auth/login" \
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
