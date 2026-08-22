# CommerceCore Infrastructure Startup Script (PowerShell)
$ErrorActionPreference = "Stop"

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$rootDir = Split-Path -Parent (Resolve-Path $scriptDir)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE INFRASTRUCTURE STARTUP" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Load .env Configuration
$envFile = Join-Path $rootDir ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

$kafkaPort = if ($env:KAFKA_PORT) { [int]$env:KAFKA_PORT } else { 9092 }

# Start postgres, redis, kafka
Write-Host "Starting PostgreSQL, Redis, and Kafka in lightweight Docker mode..." -ForegroundColor Yellow
docker compose -f "$rootDir\docker-compose.yml" up -d postgres redis kafka

# Wait for healthy state
Write-Host "Waiting for infrastructure to become healthy..." -ForegroundColor Yellow

$infraReady = $false
$timeout = 45
$elapsed = 0
while ($elapsed -lt $timeout) {
    $pgOk = $false
    $redisOk = $false
    $kafkaOk = $false

    try {
        $tcpPg = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue
        $pgOk = $tcpPg.TcpTestSucceeded
    } catch {}

    try {
        $tcpRedis = Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -WarningAction SilentlyContinue
        $redisOk = $tcpRedis.TcpTestSucceeded
    } catch {}

    try {
        $tcpKafka = Test-NetConnection -ComputerName 127.0.0.1 -Port $kafkaPort -WarningAction SilentlyContinue
        $kafkaOk = $tcpKafka.TcpTestSucceeded
    } catch {}

    if ($pgOk -and $redisOk -and $kafkaOk) {
        $infraReady = $true
        break
    }

    Start-Sleep -Seconds 2
    $elapsed += 2
}

if ($infraReady) {
    Write-Host "PostgreSQL (:5432)        -> READY" -ForegroundColor Green
    Write-Host "Redis (:6379)             -> READY" -ForegroundColor Green
    Write-Host "Kafka (:$kafkaPort)           -> READY" -ForegroundColor Green
    Write-Host "Infrastructure is ready for local microservice execution!" -ForegroundColor Green
} else {
    Write-Host "Warning: Some infrastructure components may still be starting up." -ForegroundColor Yellow
}
Write-Host "==================================================" -ForegroundColor Cyan
