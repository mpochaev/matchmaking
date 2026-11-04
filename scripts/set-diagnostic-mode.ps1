param(
    [ValidateSet('NORMAL','SLOW','FAIL')][string]$Mode = 'NORMAL',
    [ValidateRange(1,120)][int]$Seconds = 90,
    [string]$ServiceUrl = 'http://localhost:8083',
    [string]$Issuer = 'http://localhost:8085/realms/matchmaking'
)
$ErrorActionPreference = 'Stop'
# Учебные credentials из импортированного realm. Токен не выводится.
$operator = Invoke-RestMethod -Method Post -Uri "$Issuer/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' -Body @{
        grant_type='client_credentials'; client_id='matchmaking-ops-client'; client_secret='matchmaking-ops-secret'
    }
Invoke-RestMethod -Method Put -Headers @{Authorization="Bearer $($operator.access_token)"} `
    -Uri "$ServiceUrl/internal/admin/failure-mode?mode=$Mode&seconds=$Seconds"
