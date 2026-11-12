param(
    [ValidateSet('NORMAL','LATENCY','DISCONNECT')][string]$Mode = 'NORMAL',
    [ValidateRange(0,10000)][int]$LatencyMs = 1300,
    [ValidateRange(0,1000)][int]$JitterMs = 100,
    [string]$ProxyApi = 'http://127.0.0.1:8474'
)
$ErrorActionPreference = 'Stop'
$uri = "$ProxyApi/proxies/rating"
$proxy = Invoke-RestMethod $uri -UserAgent 'sop-lab08'
if ($proxy.upstream -ne 'host.docker.internal:8083' -or $proxy.listen -notin @('0.0.0.0:8666','[::]:8666')) {
    throw 'Параметры proxy отличаются от учебного стенда; изменение отменено.'
}
$toxicName = 'matchmaking-lab08-latency'
if (@($proxy.toxics | Where-Object name -eq $toxicName).Count) {
    $null = Invoke-RestMethod -Method Delete "$uri/toxics/$toxicName" -UserAgent 'sop-lab08'
}
$body = @{name='rating';listen=$proxy.listen;upstream=$proxy.upstream;enabled=($Mode -ne 'DISCONNECT')} | ConvertTo-Json
$null = Invoke-RestMethod -Method Post -Uri $uri -ContentType 'application/json' -Body $body -UserAgent 'sop-lab08'
if ($Mode -eq 'LATENCY') {
    $body = @{name=$toxicName;type='latency';stream='downstream';toxicity=1.0;
        attributes=@{latency=$LatencyMs;jitter=$JitterMs}} | ConvertTo-Json -Depth 4
    $null = Invoke-RestMethod -Method Post -Uri "$uri/toxics" -ContentType 'application/json' -Body $body -UserAgent 'sop-lab08'
}
Invoke-RestMethod $uri -UserAgent 'sop-lab08' | Select-Object name,enabled,toxics
