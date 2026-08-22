# CommerceCore Infrastructure Teardown Script (PowerShell)
$ErrorActionPreference = "Stop"

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
if (-not $scriptDir) { $scriptDir = "." }
$rootDir = Split-Path -Parent (Resolve-Path $scriptDir)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE INFRASTRUCTURE TEARDOWN" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

docker compose -f "$rootDir\docker-compose.yml" stop postgres redis kafka
Write-Host "Infrastructure containers stopped." -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Cyan
