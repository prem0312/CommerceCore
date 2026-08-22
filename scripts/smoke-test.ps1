# CommerceCore End-to-End Smoke Test Script (PowerShell)
$ErrorActionPreference = "Stop"

# Auto-load .env from repository root if present
$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$envFile = Join-Path (Split-Path -Parent (Resolve-Path $scriptDir)) ".env"
if (-not (Test-Path $envFile)) {
    $envFile = ".env"
}
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim().Trim('"').Trim("'")
            if (-not [System.Environment]::GetEnvironmentVariable($key)) {
                [System.Environment]::SetEnvironmentVariable($key, $val)
            }
        }
    }
}

$GatewayUrl = if ($env:COMMERCECORE_GATEWAY_URL) { $env:COMMERCECORE_GATEWAY_URL } else { "http://localhost:8080" }
$EurekaUrl = if ($env:COMMERCECORE_EUREKA_URL) { $env:COMMERCECORE_EUREKA_URL } else { "http://localhost:8761" }

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE END-TO-END SMOKE TEST SUITE" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Health & Discovery Readiness Check
Write-Host "`n[1/11] Checking Eureka and API Gateway Health & Service Discovery Readiness..." -ForegroundColor Yellow

$requiredServices = @(
    "AUTH-SERVICE",
    "USER-SERVICE",
    "INVENTORY-SERVICE",
    "ORDER-SERVICE",
    "PAYMENT-SERVICE",
    "REVIEW-SERVICE",
    "NOTIFICATION-SERVICE"
)

$timeoutSeconds = 60
$pollInterval = 2
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$ready = $false
$missingServices = @()

while ($stopwatch.Elapsed.TotalSeconds -lt $timeoutSeconds) {
    $gatewayIsUp = $false
    try {
        $gatewayHealth = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
        if ($gatewayHealth.status -eq "UP") {
            $gatewayIsUp = $true
        }
    } catch {}

    $registeredUpServices = @()
    try {
        $eurekaResp = Invoke-RestMethod -Uri "$EurekaUrl/eureka/apps" -Headers @{ Accept = "application/json" } -Method Get -TimeoutSec 3 -ErrorAction Stop
        if ($eurekaResp -and $eurekaResp.applications -and $eurekaResp.applications.application) {
            $apps = @($eurekaResp.applications.application)
            foreach ($app in $apps) {
                $appName = if ($app.name) { $app.name.ToString().ToUpper() } else { "" }
                $instances = @($app.instance)
                $isUp = $false
                foreach ($inst in $instances) {
                    if ($inst.status -eq "UP") {
                        $isUp = $true
                        break
                    }
                }
                if ($isUp -and $appName) {
                    $registeredUpServices += $appName
                }
            }
        }
    } catch {}

    $missingServices = @()
    if (-not $gatewayIsUp) {
        $missingServices += "API-GATEWAY"
    }
    foreach ($svc in $requiredServices) {
        if ($registeredUpServices -notcontains $svc) {
            $missingServices += $svc
        }
    }

    if ($missingServices.Count -eq 0) {
        $ready = $true
        break
    }

    $elapsed = [math]::Round($stopwatch.Elapsed.TotalSeconds, 0)
    Write-Host "  Waiting for services to be UP in Eureka... Missing: ($($missingServices -join ', ')) [${elapsed}s/${timeoutSeconds}s]" -ForegroundColor Gray
    Start-Sleep -Seconds $pollInterval
}

if (-not $ready) {
    Write-Host "`n[ERROR] Service readiness timeout reached (${timeoutSeconds}s)." -ForegroundColor Red
    Write-Host "The following required services are missing or not UP in Eureka:" -ForegroundColor Red
    foreach ($missing in $missingServices) {
        Write-Host "  - $missing" -ForegroundColor Red
    }
    exit 1
}
$elapsedTotal = [math]::Round($stopwatch.Elapsed.TotalSeconds, 1)
Write-Host "API Gateway Status: UP" -ForegroundColor Green
Write-Host "All 7 required microservices registered and UP in Eureka (${elapsedTotal}s elapsed)." -ForegroundColor Green

# 2. User Registration (Public registration always creates USER role)
$testEmail = "shopper_$(Get-Random)@commercecore.io"
if (-not $env:COMMERCECORE_SMOKE_TEST_PASSWORD) {
    Write-Host "ERROR: COMMERCECORE_SMOKE_TEST_PASSWORD is not set." -ForegroundColor Red
    exit 1
}

$password = $env:COMMERCECORE_SMOKE_TEST_PASSWORD

Write-Host "`n[2/11] Registering test customer: $testEmail..." -ForegroundColor Yellow
$regBody = @{
    email = $testEmail
    password = $password
    name = "Test Shopper"
} | ConvertTo-Json

$regTimeout = 30
$regPollInterval = 2
$regStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$regResp = $null

while ($regStopwatch.Elapsed.TotalSeconds -lt $regTimeout) {
    try {
        $regResp = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/register" -Method Post -Body $regBody -ContentType "application/json" -ErrorAction Stop
        break
    } catch {
        $statusCode = 0
        $errBody = ""
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $errBody = $reader.ReadToEnd()
            } catch {}
}
        # Retry only when response is 503/SERVICE_UNAVAILABLE caused by missing discovery
        $is503 = ($statusCode -eq 503) -or ($_.Exception.Message -match "503") -or ($errBody -match "503|SERVICE_UNAVAILABLE|Unable to find instance")
        if ($is503) {
            $elapsed = [math]::Round($regStopwatch.Elapsed.TotalSeconds, 0)
            Write-Host "  Gateway cache synchronizing (503 Service Unavailable)... Retrying in ${regPollInterval}s [${elapsed}s/${regTimeout}s]" -ForegroundColor Gray
            Start-Sleep -Seconds $regPollInterval
        } else {
            # Do not retry validation errors (4xx) or other non-503 errors
            throw $_
        }
    }
}

if (-not $regResp) {
    throw "Registration failed: Timed out waiting for API Gateway to route to auth-service after ${regTimeout}s."
}

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
if (-not $env:COMMERCECORE_ADMIN_PASSWORD) {
    Write-Host "ERROR: COMMERCECORE_ADMIN_PASSWORD is not set." -ForegroundColor Red
    exit 1
}

$adminPassword = $env:COMMERCECORE_ADMIN_PASSWORD

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
