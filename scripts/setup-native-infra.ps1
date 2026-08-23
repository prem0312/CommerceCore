# CommerceCore Native Infrastructure Setup Script (PowerShell)
# Initializes PostgreSQL databases, Redis discovery, and Kafka KRaft cluster storage idempotently.
$ErrorActionPreference = "Stop"

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$rootDir = Split-Path -Parent (Resolve-Path $scriptDir)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE NATIVE INFRASTRUCTURE SETUP" -ForegroundColor Cyan
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

# Ensure logs directory exists
$logsDir = Join-Path $rootDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

# 2. Verify Java Environment
Write-Host "`n[1/4] Checking Java Runtime..." -ForegroundColor Yellow
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) {
    $javaVer = (cmd.exe /c "java -version 2>&1") | Select-Object -First 1
    Write-Host "  Java is available: $javaVer" -ForegroundColor Gray
} else {
    Write-Host "  [WARNING] Java was not found in PATH. Java 25 LTS is required." -ForegroundColor Red
}

# 3. Discover & Configure PostgreSQL
Write-Host "`n[2/4] Checking PostgreSQL Native Setup..." -ForegroundColor Yellow
$pgPaths = @(
    "C:\Program Files\PostgreSQL\17\bin",
    "C:\Program Files\PostgreSQL\16\bin",
    "C:\Program Files\PostgreSQL\15\bin",
    "D:\Program Files\PostgreSQL\16\bin",
    "C:\PostgreSQL\bin"
)
foreach ($p in $pgPaths) {
    if ((Test-Path $p) -and ($env:PATH -notmatch [regex]::Escape($p))) {
        $env:PATH = "$p;$env:PATH"
    }
}

$psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
$pgUser = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { "postgres" }
$pgPass = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { "postgres" }
$pgPort = if ($env:POSTGRES_PORT) { [int]$env:POSTGRES_PORT } else { 5432 }

if ($psqlCmd) {
    Write-Host "  Found psql at: $($psqlCmd.Source)" -ForegroundColor Green

    # Check if PostgreSQL service is running or port 5432 is open
    $pgPortOpen = (Test-NetConnection -ComputerName 127.0.0.1 -Port $pgPort -WarningAction SilentlyContinue).TcpTestSucceeded
    if (-not $pgPortOpen) {
        $pgService = Get-Service -Name *postgres* -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($pgService -and $pgService.Status -ne "Running") {
            Write-Host "  Starting PostgreSQL Windows service ($($pgService.Name))..." -ForegroundColor Yellow
            Start-Service $pgService.Name -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 3
        }
    }

    # Initialize Databases Idempotently
    $databases = @(
        "commercecore_auth",
        "commercecore_user",
        "commercecore_inventory",
        "commercecore_order",
        "commercecore_payment",
        "commercecore_notification",
        "commercecore_review"
    )

    $env:PGPASSWORD = $pgPass
    try {
        Write-Host "  Connecting to PostgreSQL on port $pgPort..." -ForegroundColor Gray
        $existingDbsRaw = & psql -U $pgUser -h 127.0.0.1 -p $pgPort -d postgres -t -A -c "SELECT datname FROM pg_database WHERE datistemplate = false;" 2>$null
        $existingDbs = @($existingDbsRaw | ForEach-Object { $_.Trim() })

        foreach ($db in $databases) {
            if ($existingDbs -contains $db) {
                Write-Host "  Database '$db' already exists -> OK" -ForegroundColor Gray
            } else {
                Write-Host "  Creating database '$db'..." -ForegroundColor Green
                & psql -U $pgUser -h 127.0.0.1 -p $pgPort -d postgres -c "CREATE DATABASE $db;" | Out-Null
            }
        }
        Write-Host "  All 7 CommerceCore databases verified successfully!" -ForegroundColor Green
    } catch {
        Write-Host "  [NOTICE] Could not automatically initialize databases: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "  Please ensure PostgreSQL is running with user '$pgUser' on port $pgPort." -ForegroundColor Gray
    }
} else {
    Write-Host "  [NOTICE] PostgreSQL 'psql' CLI not found in PATH." -ForegroundColor Yellow
    Write-Host "  To install PostgreSQL natively on Windows:" -ForegroundColor Cyan
    Write-Host "    winget install PostgreSQL.PostgreSQL.16" -ForegroundColor White
}

# 4. Discover & Configure Redis
Write-Host "`n[3/4] Checking Redis Native Setup..." -ForegroundColor Yellow
$redisPaths = @(
    "C:\Program Files\Memurai",
    "C:\Program Files\Redis",
    "C:\redis",
    "C:\tools\redis"
)
$wingetRedis = Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\taizod1024.redis-windows-fork*" -Recurse -Filter "redis-server.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty DirectoryName -First 1
if ($wingetRedis) { $redisPaths += $wingetRedis }

foreach ($p in $redisPaths) {
    if ((Test-Path $p) -and ($env:PATH -notmatch [regex]::Escape($p))) {
        $env:PATH = "$p;$env:PATH"
    }
}

$redisCmd = Get-Command redis-server, memurai -ErrorAction SilentlyContinue
$redisCliCmd = Get-Command redis-cli, memurai-cli -ErrorAction SilentlyContinue

if ($redisCmd -or $redisCliCmd) {
    $foundName = if ($redisCmd) { $redisCmd[0].Name } else { $redisCliCmd[0].Name }
    Write-Host "  Found Redis-compatible engine ($foundName)." -ForegroundColor Green
} else {
    Write-Host "  [NOTICE] Redis / Memurai was not found in standard paths." -ForegroundColor Yellow
    Write-Host "  Recommended native options for Windows:" -ForegroundColor Cyan
    Write-Host "    Option A (Production-grade Memurai Developer): winget install Memurai.MemuraiDeveloper" -ForegroundColor White
    Write-Host "    Option B (Redis for Windows):                  winget install taizod1024.redis-windows-fork" -ForegroundColor White
}

# 5. Discover & Configure Kafka (KRaft Mode)
Write-Host "`n[4/4] Checking Apache Kafka KRaft Setup..." -ForegroundColor Yellow
$kafkaPaths = @(
    "C:\kafka\bin\windows",
    "D:\kafka\bin\windows",
    "$HOME\kafka\bin\windows",
    "C:\tools\kafka\bin\windows",
    (Join-Path $rootDir "infrastructure\kafka\bin\windows")
)
foreach ($p in $kafkaPaths) {
    if ((Test-Path $p) -and ($env:PATH -notmatch [regex]::Escape($p))) {
        $env:PATH = "$p;$env:PATH"
    }
}

$kafkaStorageCmd = Get-Command kafka-storage.bat, kafka-storage -ErrorAction SilentlyContinue
$kafkaConfig = Join-Path $rootDir "infrastructure\kafka\kraft-server.properties"

if ($kafkaStorageCmd) {
    Write-Host "  Found Kafka tools at: $($kafkaStorageCmd.Source)" -ForegroundColor Green

    # Check if KRaft storage directory is already formatted
    $kraftLogsDir = Join-Path $rootDir "logs\kafka-kraft-logs"
    $metaFile = Join-Path $kraftLogsDir "meta.properties"

    if (-not (Test-Path $metaFile)) {
        Write-Host "  Formatting Kafka KRaft storage with cluster ID: MkU3OEVBNTcwNTJENDM2Qk..." -ForegroundColor Yellow
        try {
            & kafka-storage.bat format -t "MkU3OEVBNTcwNTJENDM2Qk" -c "$kafkaConfig" --ignore-formatted | Out-Null
            Write-Host "  Kafka KRaft cluster storage formatted successfully!" -ForegroundColor Green
        } catch {
            Write-Host "  [WARNING] Kafka storage format exited with: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  Kafka KRaft storage already formatted at logs\kafka-kraft-logs -> OK" -ForegroundColor Gray
    }
} else {
    Write-Host "  [NOTICE] Apache Kafka binaries not found in PATH." -ForegroundColor Yellow
    Write-Host "  To install Apache Kafka natively on Windows:" -ForegroundColor Cyan
    Write-Host "    1. Download Apache Kafka 3.6+ binary (.tgz) from https://kafka.apache.org/downloads" -ForegroundColor White
    Write-Host "    2. Extract to C:\kafka and add C:\kafka\bin\windows to your PATH." -ForegroundColor White
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "  SETUP SCRIPT COMPLETED" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Next steps:" -ForegroundColor White
Write-Host "  1. Verify PostgreSQL, Redis, and Kafka prerequisites are installed." -ForegroundColor Gray
Write-Host "  2. Start infrastructure: .\scripts\start-infra.ps1" -ForegroundColor Gray
Write-Host "  3. Start services:       .\scripts\start-local.ps1" -ForegroundColor Gray
Write-Host "==================================================" -ForegroundColor Cyan
