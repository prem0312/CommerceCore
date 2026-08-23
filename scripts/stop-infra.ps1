# CommerceCore Native Infrastructure Teardown Script (PowerShell)
$ErrorActionPreference = "SilentlyContinue"

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$rootDir = Split-Path -Parent (Resolve-Path $scriptDir)
$logsDir = Join-Path $rootDir "logs"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE NATIVE INFRASTRUCTURE TEARDOWN" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# Safely stop only background processes started by CommerceCore
$pidFiles = @("kafka.pid", "redis.pid")
foreach ($pf in $pidFiles) {
    $pidPath = Join-Path $logsDir $pf
    if (Test-Path $pidPath) {
        $pId = Get-Content $pidPath -ErrorAction SilentlyContinue
        if ($pId -and $pId -match '^\d+$') {
            $proc = Get-Process -Id ([int]$pId) -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "Stopping CommerceCore-managed process $($proc.ProcessName) (PID: $pId)..." -ForegroundColor Yellow
                Stop-Process -Id ([int]$pId) -Force -ErrorAction SilentlyContinue
            }
        }
        Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "CommerceCore native infrastructure processes stopped." -ForegroundColor Green
Write-Host "Note: PostgreSQL and system-managed services remain unaffected." -ForegroundColor Gray
Write-Host "==================================================" -ForegroundColor Cyan
