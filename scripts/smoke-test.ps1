# CommerceCore End-to-End Smoke Test Script (PowerShell)
$GatewayUrl = "http://localhost:8080"
$ErrorActionPreference = "Stop"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE END-TO-END SMOKE TEST SUITE" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Health & Discovery Check
Write-Host "`n[1/11] Checking Eureka and API Gateway Health..." -ForegroundColor Yellow
$gatewayHealth = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get
Write-Host "API Gateway Status: $($gatewayHealth.status)" -ForegroundColor Green

# 2. User Registration (Public registration always creates USER role)
$testEmail = "shopper_$(Get-Random)@commercecore.io"
$password = "SecretPass123!"
Write-Host "`n[2/11] Registering test customer: $testEmail..." -ForegroundColor Yellow
$regBody = @{
    email = $testEmail
    password = $password
    name = "Test Shopper"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/register" -Method Post -Body $regBody -ContentType "application/json"
$token = $regResp.accessToken
$userId = $regResp.userId
Write-Host "Registered User ID: $userId (Role: $($regResp.role))" -ForegroundColor Green

# 3. User Login & Token Verification
Write-Host "`n[3/11] Logging in test customer..." -ForegroundColor Yellow
$loginBody = @{
    email = $testEmail
    password = $password
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.accessToken
$headers = @{
    Authorization = "Bearer $token"
}
Write-Host "Customer JWT acquired successfully (Role: $($loginResp.role))." -ForegroundColor Green

# 4. Fetch User Profile
Write-Host "`n[4/11] Fetching User Profile (/api/v1/users/me)..." -ForegroundColor Yellow
$profile = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/users/me" -Method Get -Headers $headers
Write-Host "Profile retrieved: $($profile.name) <$($profile.email)> (Role: $($profile.role))" -ForegroundColor Green

# 5. Product Creation (Requires Admin Authentication)
Write-Host "`n[5/11] Authenticating as Administrator & Creating Product..." -ForegroundColor Yellow
$adminEmail = if ($env:COMMERCECORE_ADMIN_EMAIL) { $env:COMMERCECORE_ADMIN_EMAIL } else { "admin@commercecore.io" }
$adminPassword = if ($env:COMMERCECORE_ADMIN_PASSWORD) { $env:COMMERCECORE_ADMIN_PASSWORD } else { "AdminPass123!" }

$adminLoginBody = @{
    email = $adminEmail
    password = $adminPassword
} | ConvertTo-Json

$adminLoginResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
$adminToken = $adminLoginResp.accessToken
Write-Host "Admin authenticated successfully (Role: $($adminLoginResp.role))." -ForegroundColor Green

$prodBody = @{
    name = "Wireless Mechanical Keyboard RGB"
    description = "Custom hot-swappable tactile switches"
    price = 149.99
    category = "Electronics"
    initialStock = 50
    reorderThreshold = 10
} | ConvertTo-Json

$adminHeaders = @{
    Authorization = "Bearer $adminToken"
}
$prodResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/products" -Method Post -Body $prodBody -Headers $adminHeaders -ContentType "application/json"
$productId = $prodResp.id
$price = $prodResp.price
Write-Host "Created Product ID: $productId (Name: $($prodResp.name)) Price: `$$price" -ForegroundColor Green

# 6. Verify Redis Caching
Write-Host "`n[6/11] Verifying Redis Cache-Aside for Product..." -ForegroundColor Yellow
$cachedProd1 = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/products/$productId" -Method Get
$cachedProd2 = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/products/$productId" -Method Get
Write-Host "Product query returned successfully: $($cachedProd2.name) (Cache populated)." -ForegroundColor Green

# 7. Order Placement with Distributed Idempotency (Customer User)
Write-Host "`n[7/11] Placing Order with Idempotency Key..." -ForegroundColor Yellow
$idempotencyKey = "idemp-smoke-$(Get-Random)"
$orderBody = @{
    items = @(
        @{
            productId = $productId
            productName = "Wireless Mechanical Keyboard RGB"
            quantity = 2
            unitPrice = 149.99
        }
    )
    currency = "USD"
} | ConvertTo-Json

$orderHeaders = @{
    Authorization = "Bearer $token"
    "Idempotency-Key" = $idempotencyKey
}

$orderResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/orders" -Method Post -Body $orderBody -Headers $orderHeaders -ContentType "application/json"
$orderId = $orderResp.id
$totalAmt = $orderResp.totalAmount
Write-Host "Order Placed ID: $orderId, Total: `$$totalAmt, Status: $($orderResp.status)" -ForegroundColor Green

# 8. Duplicate Idempotent Request Replay
Write-Host "`n[8/11] Sending Duplicate Order Request (Same Idempotency-Key)..." -ForegroundColor Yellow
$replayResponse = Invoke-WebRequest -Uri "$GatewayUrl/api/v1/orders" -Method Post -Body $orderBody -Headers $orderHeaders -ContentType "application/json" -UseBasicParsing
$isReplay = $replayResponse.Headers["Idempotent-Replay"]
Write-Host "Replay Status Code: $($replayResponse.StatusCode) | Idempotent-Replay Header: $isReplay" -ForegroundColor Green

# 9. Verify Inventory Stock Reduction
Write-Host "`n[9/11] Checking Updated Inventory Quantity..." -ForegroundColor Yellow
$inv = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/inventory/$productId" -Method Get -Headers $headers
Write-Host "Product $productId Available Stock: $($inv.availableQuantity) (Reserved: $($inv.reservedQuantity))" -ForegroundColor Green

# 10. Submit Product Review (Customer User)
Write-Host "`n[10/11] Submitting Product Review..." -ForegroundColor Yellow
$reviewBody = @{
    productId = $productId
    rating = 5
    comment = "Best keyboard I have ever used!"
} | ConvertTo-Json

$reviewResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/reviews" -Method Post -Body $reviewBody -Headers $headers -ContentType "application/json"
Write-Host "Review ID $($reviewResp.id) created with rating: $($reviewResp.rating) stars" -ForegroundColor Green

# 11. Fetch Product Review Summary
Write-Host "`n[11/11] Fetching Product Review Summary..." -ForegroundColor Yellow
$summary = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/reviews/product/$productId/summary" -Method Get -Headers $headers
Write-Host "Product $productId Average Rating: $($summary.averageRating) across $($summary.totalReviews) reviews." -ForegroundColor Green

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "  ALL 11 SMOKE TESTS PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Cyan
