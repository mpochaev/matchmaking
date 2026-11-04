param(
    [ValidateRange(1,1000)][int]$Count = 30,
    [ValidateRange(0,5000)][int]$IntervalMs = 200,
    [string]$ServiceUrl = 'http://localhost:8083',
    [string]$Issuer = 'http://localhost:8085/realms/matchmaking'
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Нужен PowerShell 7.' }
$token = & "$PSScriptRoot/get-service-token.ps1" -Issuer $Issuer
$statuses = @{}
for ($i=0; $i -lt $Count; $i++) {
    $response = Invoke-WebRequest -Uri "$ServiceUrl/internal/ratings/$([guid]::NewGuid())" `
        -Headers @{Authorization="Bearer $token"} -SkipHttpErrorCheck
    $status = [string][int]$response.StatusCode
    if ($status -notin @('200','503')) { throw "Неожиданный HTTP $status; проверьте токен и настройки." }
    $statuses[$status] = 1 + [int]$statuses[$status]
    if ($IntervalMs -gt 0) { Start-Sleep -Milliseconds $IntervalMs }
}
[pscustomobject]@{Requests=$Count; Statuses=($statuses | ConvertTo-Json -Compress)}
