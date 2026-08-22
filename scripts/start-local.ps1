# CommerceCore Local Services Startup Script (PowerShell)
$ErrorActionPreference = "Stop"

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$rootDir = Split-Path -Parent (Resolve-Path $scriptDir)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE LOCAL SERVICES STARTUP" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Load .env Configuration
$envFile = Join-Path $rootDir ".env"
if (-not (Test-Path $envFile)) {
    $exampleEnv = Join-Path $rootDir ".env.example"
    if (Test-Path $exampleEnv) {
        Write-Host "Creating .env from .env.example..." -ForegroundColor Yellow
        Copy-Item $exampleEnv $envFile
    }
}

if (Test-Path $envFile) {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Gray
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

# 2. Check Backing Infrastructure (PostgreSQL, Redis, Kafka)
Write-Host "`n[1/4] Checking backing infrastructure..." -ForegroundColor Yellow
$kafkaPort = if ($env:KAFKA_PORT) { [int]$env:KAFKA_PORT } else { 9092 }
$pgReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue).TcpTestSucceeded
$redisReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -WarningAction SilentlyContinue).TcpTestSucceeded
$kafkaReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $kafkaPort -WarningAction SilentlyContinue).TcpTestSucceeded

if (-not ($pgReady -and $redisReady -and $kafkaReady)) {
    Write-Host "Starting infrastructure containers (PostgreSQL, Redis, Kafka)..." -ForegroundColor Yellow
    docker compose -f "$rootDir\docker-compose.yml" up -d postgres redis kafka
    Start-Sleep -Seconds 5
}

# 3. Create Logs Directory
$logsDir = Join-Path $rootDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

# 4. Define Microservice Startup Sequence
$eurekaPort = 8700
if ($env:EUREKA_SERVER_URL -match ':(\d+)') {
    $eurekaPort = [int]$matches[1]
} elseif ($env:COMMERCECORE_EUREKA_URL -match ':(\d+)') {
    $eurekaPort = [int]$matches[1]
}

$services = @(
    @{ Name = "eureka-service";       Port = $eurekaPort; Path = "services\eureka-service" },
    @{ Name = "api-gateway";          Port = 8080;        Path = "services\api-gateway" },
    @{ Name = "auth-service";         Port = 8081;        Path = "services\auth-service" },
    @{ Name = "user-service";         Port = 8082;        Path = "services\user-service" },
    @{ Name = "inventory-service";    Port = 8083;        Path = "services\inventory-service" },
    @{ Name = "order-service";        Port = 8084;        Path = "services\order-service" },
    @{ Name = "payment-service";      Port = 8085;        Path = "services\payment-service" },
    @{ Name = "notification-service"; Port = 8086;        Path = "services\notification-service" },
    @{ Name = "review-service";       Port = 8087;        Path = "services\review-service" }
)

Write-Host "`n[2/4] Verifying packaged JARs..." -ForegroundColor Yellow
foreach ($svc in $services) {
    $targetDir = Join-Path $rootDir "$($svc.Path)\target"
    $jar = Get-ChildItem -Path "$targetDir\*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch '\.original$' } | Select-Object -First 1
    if (-not $jar) {
        Write-Host "Building project modules with Maven..." -ForegroundColor Yellow
        Push-Location $rootDir
        mvn package -DskipTests
        Pop-Location
        break
    }
}

# 5. Launch Microservices in Dependency Order
Write-Host "`n[3/4] Starting microservices..." -ForegroundColor Yellow
$runningProcesses = @()

foreach ($svc in $services) {
    $name = $svc.Name
    $port = $svc.Port
    $svcDir = Join-Path $rootDir $svc.Path
    $targetDir = Join-Path $svcDir "target"
    $jar = Get-ChildItem -Path "$targetDir\*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch '\.original$' } | Select-Object -First 1

    if (-not $jar) {
        Write-Host "[ERROR] Could not find JAR for $name in $targetDir" -ForegroundColor Red
        exit 1
    }

    # Check if already listening on port
    $existing = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "  $name is already running on port $port (PID: $($existing[0].OwningProcess))" -ForegroundColor Gray
        continue
    }

    $stdOut = Join-Path $logsDir "$name.log"
    $stdErr = Join-Path $logsDir "$name.err.log"

    $javaExe = (Get-Command javaw -ErrorAction SilentlyContinue).Source
    if (-not $javaExe) { $javaExe = (Get-Command java).Source }

    $javaArgs = @(
        "-Dnet.bytebuddy.experimental=true",
        "-XX:+EnableDynamicAgentLoading"
    )

    if ($name -eq "eureka-service") {
        $javaArgs += "-Dserver.port=$eurekaPort"
    }

    $javaArgs += @("-jar", "`"$($jar.FullName)`"")

    $envCommands = @()
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
                $k = $matches[1].Trim()
                $v = $matches[2].Trim().Trim('"').Trim("'")
                $envCommands += "set `"$k=$v`""
            }
        }
    }
    $envPrefix = if ($envCommands.Count -gt 0) { ($envCommands -join " & ") + " & " } else { "" }
    $wrapperCmd = "cmd.exe /c `"$envPrefix `"$javaExe`" $($javaArgs -join ' ') > `"$stdOut`" 2> `"$stdErr`"`""

    $procRes = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine = $wrapperCmd
        CurrentDirectory = $svcDir
    }
    $procId = $procRes.ProcessId
    Write-Host "  Started $name (PID: $procId) on port $port -> Log: logs\$name.log" -ForegroundColor Green

    # Special wait for Eureka to accept registrations before downstream services connect
    if ($name -eq "eureka-service") {
        Write-Host "  Waiting for Eureka Server to become healthy..." -ForegroundColor Gray
        $eurekaReady = $false
        for ($i = 0; $i -lt 30; $i++) {
            try {
                $h = Invoke-RestMethod -Uri "http://localhost:$eurekaPort/actuator/health" -TimeoutSec 2 -ErrorAction Stop
                if ($h.status -eq "UP") { $eurekaReady = $true; break }
            } catch {}
            Start-Sleep -Seconds 1
        }
        if ($eurekaReady) {
            Write-Host "  Eureka Server is UP on port $eurekaPort!" -ForegroundColor Green
        }
    }
}

# 6. Verify Service Readiness
Write-Host "`n[4/4] Verifying service readiness & Eureka registration..." -ForegroundColor Yellow
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$timeoutSec = 60
$allUp = $false

while ($stopwatch.Elapsed.TotalSeconds -lt $timeoutSec) {
    $apps = @()
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:$eurekaPort/eureka/apps" -Headers @{ Accept = "application/json" } -TimeoutSec 3 -ErrorAction Stop
        if ($resp -and $resp.applications -and $resp.applications.application) {
            $apps = @($resp.applications.application | ForEach-Object { $_.name.ToUpper() })
        }
    } catch {}

    $expected = @("API-GATEWAY", "AUTH-SERVICE", "USER-SERVICE", "INVENTORY-SERVICE", "ORDER-SERVICE", "PAYMENT-SERVICE", "NOTIFICATION-SERVICE", "REVIEW-SERVICE")
    $missing = @($expected | Where-Object { $apps -notcontains $_ })

    if ($missing.Count -eq 0) {
        $allUp = $true
        break
    }

    $elapsedSec = [math]::Round($stopwatch.Elapsed.TotalSeconds, 0)
    Write-Host "  Registered in Eureka: ($($apps -join ', ')) | Waiting for: ($($missing -join ', ')) [${elapsedSec}s/${timeoutSec}s]" -ForegroundColor Gray
    Start-Sleep -Seconds 3
}

Write-Host "`n==================================================" -ForegroundColor Cyan
if ($allUp) {
    Write-Host "  ALL COMMERCECORE SERVICES STARTED SUCCESSFULLY!" -ForegroundColor Green
} else {
    Write-Host "  Services launched. Eureka registration will finalize shortly." -ForegroundColor Yellow
}
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Eureka Dashboard:    http://localhost:$eurekaPort" -ForegroundColor White
Write-Host "API Gateway:         http://localhost:8080" -ForegroundColor White
Write-Host "Actuator Health:     http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "To run smoke tests:  .\scripts\smoke-test.ps1" -ForegroundColor White
Write-Host "To stop services:    .\scripts\stop-local.ps1" -ForegroundColor White
Write-Host "==================================================" -ForegroundColor Cyan
