# CommerceCore Native Infrastructure Startup & Verification Script (PowerShell)
$ErrorActionPreference = "Stop"

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$rootDir = Split-Path -Parent (Resolve-Path $scriptDir)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE NATIVE INFRASTRUCTURE STARTUP" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Load .env Configuration
$envFile = Join-Path $rootDir ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment configuration from .env..." -ForegroundColor Gray
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

$logsDir = Join-Path $rootDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

$pgPort = if ($env:POSTGRES_PORT) { [int]$env:POSTGRES_PORT } else { 5432 }
$redisPort = if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 }
$kafkaPort = if ($env:KAFKA_PORT) { [int]$env:KAFKA_PORT } else { 29092 }

# Dynamically augment PATH with standard installation directories
$standardPaths = @(
    "C:\Program Files\PostgreSQL\17\bin",
    "C:\Program Files\PostgreSQL\16\bin",
    "C:\Program Files\PostgreSQL\15\bin",
    "C:\PostgreSQL\bin",
    "C:\Program Files\Memurai",
    "C:\Program Files\Redis",
    "C:\redis",
    "C:\tools\redis",
    "C:\kafka\bin\windows",
    "D:\kafka\bin\windows",
    "$HOME\kafka\bin\windows",
    "C:\tools\kafka\bin\windows",
    (Join-Path $rootDir "infrastructure\kafka\bin\windows")
)

$wingetRedis = Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork*" -Recurse -Filter "redis-server.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty DirectoryName -First 1
if ($wingetRedis) { $standardPaths += $wingetRedis }

foreach ($p in $standardPaths) {
    if ((Test-Path $p) -and ($env:PATH -notmatch [regex]::Escape($p))) {
        $env:PATH = "$p;$env:PATH"
    }
}

# -------------------------------------------------------------
# 2. PostgreSQL Service Verification & Startup
# -------------------------------------------------------------
Write-Host "`n[1/3] Checking PostgreSQL (Port $pgPort)..." -ForegroundColor Yellow
$pgReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $pgPort -WarningAction SilentlyContinue).TcpTestSucceeded

if (-not $pgReady) {
    $pgService = Get-Service -Name *postgres* -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pgService) {
        Write-Host "  Starting PostgreSQL Windows service ($($pgService.Name))..." -ForegroundColor Yellow
        Start-Service -Name $pgService.Name -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
    }
    $pgReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $pgPort -WarningAction SilentlyContinue).TcpTestSucceeded
}

if ($pgReady) {
    Write-Host "  PostgreSQL (:$pgPort)        -> READY" -ForegroundColor Green

    # Idempotently verify databases
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlCmd) {
        $pgUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "postgres" }
        $pgPass = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "postgres" }
        $env:PGPASSWORD = $pgPass
        $databases = @("commercecore_auth", "commercecore_user", "commercecore_inventory", "commercecore_order", "commercecore_payment", "commercecore_notification", "commercecore_review")
        try {
            $existingDbsRaw = & psql -U $pgUser -h 127.0.0.1 -p $pgPort -d postgres -t -A -c "SELECT datname FROM pg_database WHERE datistemplate = false;" 2>$null
            $existingDbs = @($existingDbsRaw | ForEach-Object { $_.Trim() })
            foreach ($db in $databases) {
                if ($existingDbs -notcontains $db) {
                    Write-Host "  Creating database $db..." -ForegroundColor Gray
                    & psql -U $pgUser -h 127.0.0.1 -p $pgPort -d postgres -c "CREATE DATABASE $db;" | Out-Null
                }
            }
        } catch {}
    }
} else {
    Write-Host "  PostgreSQL (:$pgPort)        -> NOT REACHABLE" -ForegroundColor Red
    Write-Host "  [NOTICE] Please ensure PostgreSQL is running or install via:" -ForegroundColor Yellow
    Write-Host "    winget install PostgreSQL.PostgreSQL.16" -ForegroundColor Gray
}

# -------------------------------------------------------------
# 3. Redis Service Verification & Startup
# -------------------------------------------------------------
Write-Host "`n[2/3] Checking Redis (Port $redisPort)..." -ForegroundColor Yellow
$redisReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $redisPort -WarningAction SilentlyContinue).TcpTestSucceeded

if (-not $redisReady) {
    $redisService = Get-Service -Name *memurai*, *redis* -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "GameInput" } | Select-Object -First 1
    if ($redisService) {
        Write-Host "  Starting Redis/Memurai service: $($redisService.Name)..." -ForegroundColor Yellow
        Start-Service -Name $redisService.Name -ErrorAction SilentlyContinue
    } else {
        $redisExe = (Get-Command redis-server, memurai -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($redisExe) {
            Write-Host "  Launching $($redisExe.Name) as background process..." -ForegroundColor Yellow
            $redisStdOut = Join-Path $logsDir "redis.log"
            $redisStdErr = Join-Path $logsDir "redis.err.log"
            $wrapperCmd = "cmd.exe /c `"`"$($redisExe.Source)`" --port $redisPort > `"$redisStdOut`" 2> `"$redisStdErr`"`""

            $procRes = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
                CommandLine = $wrapperCmd
                CurrentDirectory = $rootDir
            }
            if ($procRes.ProcessId) {
                Set-Content -Path (Join-Path $logsDir "redis.pid") -Value $procRes.ProcessId -Force
                Write-Host "  Redis started with PID $($procRes.ProcessId)" -ForegroundColor Gray
            }
        } else {
            Write-Host "  [WARNING] Redis / Memurai executable not found in PATH." -ForegroundColor Red
            Write-Host "  Install via: winget install taizod1024.redis-windows-fork" -ForegroundColor Gray
        }
    }

    # Wait for TCP readiness
    for ($i = 0; $i -lt 15; $i++) {
        $redisReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $redisPort -WarningAction SilentlyContinue).TcpTestSucceeded
        if ($redisReady) { break }
        Start-Sleep -Seconds 1
    }
}

if ($redisReady) {
    Write-Host "  Redis (:$redisPort)             -> READY" -ForegroundColor Green
} else {
    Write-Host "  Redis (:$redisPort)             -> NOT REACHABLE" -ForegroundColor Red
}

# -------------------------------------------------------------
# 4. Kafka KRaft Broker Verification & Startup
# -------------------------------------------------------------
Write-Host "`n[3/3] Checking Apache Kafka KRaft (Port $kafkaPort)..." -ForegroundColor Yellow
$kafkaReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $kafkaPort -WarningAction SilentlyContinue).TcpTestSucceeded

if (-not $kafkaReady) {
    $kafkaServerStart = Get-Command kafka-server-start.bat, kafka-server-start -ErrorAction SilentlyContinue | Select-Object -First 1
    $kafkaStorage = Get-Command kafka-storage.bat, kafka-storage -ErrorAction SilentlyContinue | Select-Object -First 1
    $kafkaConfig = Join-Path $rootDir "infrastructure\kafka\kraft-server.properties"

    if ($kafkaServerStart -and (Test-Path $kafkaConfig)) {
        # Check KRaft storage format
        $metaFile = Join-Path $rootDir "logs\kafka-kraft-logs\meta.properties"
        if (-not (Test-Path $metaFile)) {
            if ($kafkaStorage) {
                Write-Host "  Formatting KRaft metadata storage..." -ForegroundColor Yellow
                & kafka-storage.bat format -t "MkU3OEVBNTcwNTJENDM2Qk" -c "$kafkaConfig" --ignore-formatted | Out-Null
            }
        }

        Write-Host "  Launching Kafka KRaft broker on port $kafkaPort..." -ForegroundColor Yellow
        $kafkaStdOut = Join-Path $logsDir "kafka.log"
        $kafkaStdErr = Join-Path $logsDir "kafka.err.log"

        $wrapperCmd = "cmd.exe /c `"`"$($kafkaServerStart.Source)`" `"$kafkaConfig`" > `"$kafkaStdOut`" 2> `"$kafkaStdErr`"`""
        $procRes = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
            CommandLine = $wrapperCmd
            CurrentDirectory = $rootDir
        }

        if ($procRes.ProcessId) {
            Set-Content -Path (Join-Path $logsDir "kafka.pid") -Value $procRes.ProcessId -Force
            Write-Host "  Kafka started with PID $($procRes.ProcessId)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  [WARNING] Kafka binaries or configuration not found." -ForegroundColor Red
        Write-Host "  Ensure Apache Kafka 3.6+ is extracted to C:\kafka with bin\windows in PATH." -ForegroundColor Gray
    }

    # Wait for TCP readiness
    for ($i = 0; $i -lt 30; $i++) {
        $kafkaReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port $kafkaPort -WarningAction SilentlyContinue).TcpTestSucceeded
        if ($kafkaReady) { break }
        Start-Sleep -Seconds 1
    }
}

if ($kafkaReady) {
    Write-Host "  Kafka (:$kafkaPort)           -> READY" -ForegroundColor Green

    # Idempotently verify / create required Kafka topics
    $kafkaTopicsCmd = Get-Command kafka-topics.bat, kafka-topics -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($kafkaTopicsCmd) {
        $topics = @("commercecore.orders.v1", "commercecore.payments.v1")
        foreach ($topic in $topics) {
            & kafka-topics.bat --bootstrap-server "localhost:$kafkaPort" --create --if-not-exists --topic $topic --partitions 1 --replication-factor 1 2>$null | Out-Null
        }
        Write-Host "  Verified Kafka topics: $($topics -join ', ')" -ForegroundColor Gray
    }
} else {
    Write-Host "  Kafka (:$kafkaPort)           -> NOT REACHABLE" -ForegroundColor Red
}

Write-Host "`n==================================================" -ForegroundColor Cyan
if ($pgReady -and $redisReady -and $kafkaReady) {
    Write-Host "  ALL NATIVE INFRASTRUCTURE SERVICES ARE READY!" -ForegroundColor Green
} else {
    Write-Host "  [WARNING] Some infrastructure services are not ready." -ForegroundColor Yellow
}
Write-Host "==================================================" -ForegroundColor Cyan
