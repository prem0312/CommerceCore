# CommerceCore Local Service Teardown Script (PowerShell)
$ErrorActionPreference = "SilentlyContinue"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  COMMERCECORE LOCAL SERVICES TEARDOWN" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$ports = @(8700, 8761, 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087)
$killedPids = @()

foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        $procId = $conn.OwningProcess
        if ($procId -and $procId -gt 0 -and ($killedPids -notcontains $procId)) {
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -match "java") {
                Write-Host "Stopping $($proc.ProcessName) (PID: $procId) on port $port..." -ForegroundColor Yellow
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killedPids += $procId
            }
        }
    }
}

if ($killedPids.Count -eq 0) {
    Write-Host "No running CommerceCore microservice processes were found." -ForegroundColor Green
} else {
    Write-Host "Successfully stopped $($killedPids.Count) microservice process(es)." -ForegroundColor Green
}
Write-Host "==================================================" -ForegroundColor Cyan
