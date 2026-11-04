param(
    [string]$GatewayUrl = 'http://localhost:8088',
    [switch]$SkipFaultExperiment
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Нужен PowerShell 7.' }
function Query-Prom([string]$Query) {
    $response = Invoke-RestMethod ('http://127.0.0.1:9090/api/v1/query?query='+[uri]::EscapeDataString($Query))
    if ($response.status -ne 'success') { throw "Ошибка PromQL: $Query" }
    @($response.data.result)
}
function Is-Firing {
    $alerts = (Invoke-RestMethod http://127.0.0.1:9090/api/v1/alerts).data.alerts
    @($alerts | Where-Object {$_.labels.alertname -eq 'MatchmakingRatingHighErrorRatio' -and $_.state -eq 'firing'}).Count -gt 0
}
$null = & "$PSScriptRoot/smoke-health.ps1" -GatewayUrl $GatewayUrl
$targets = @((Invoke-RestMethod http://127.0.0.1:9090/api/v1/targets).data.activeTargets)
if ($targets.Count -ne 3 -or @($targets | Where-Object {$_.health -ne 'up'}).Count) {
    throw 'В Prometheus должны быть ровно три target в состоянии UP. Проверьте /targets.'
}
$null = & "$PSScriptRoot/set-diagnostic-mode.ps1" -Mode NORMAL
$chain = & "$PSScriptRoot/verify-tracing.ps1" -GatewayUrl $GatewayUrl
$null = & "$PSScriptRoot/generate-traffic.ps1" -Count 15 -IntervalMs 100
$auth = 'Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('admin:admin-dev-only'))
$sources = @(Invoke-RestMethod -Headers @{Authorization=$auth} http://localhost:3000/api/datasources)
foreach ($uid in @('prometheus','loki','tempo')) {
    if ($uid -notin $sources.uid) { throw "В Grafana нет datasource $uid." }
    if ($uid -eq 'tempo') {
        # Tempo plugin does not implement Grafana's generic health API in this version.
        $ready = Invoke-WebRequest -Headers @{Authorization=$auth} `
            http://localhost:3000/api/datasources/proxy/uid/tempo/ready
        if ($ready.StatusCode -ne 200) { throw 'Tempo недоступен из Grafana.' }
    } else {
        $health = Invoke-RestMethod -Headers @{Authorization=$auth} "http://localhost:3000/api/datasources/uid/$uid/health"
        if ($health.status -ne 'OK') { throw "Источник $uid недоступен из Grafana." }
    }
}
$dashboard = Invoke-RestMethod -Headers @{Authorization=$auth} http://localhost:3000/api/dashboards/uid/matchmaking-golden-signals
if ($dashboard.dashboard.panels.Count -lt 9) { throw 'Dashboard не загружен.' }

# Ждем два scrape и поступления файлов через Alloy, а не предполагаем синхронный экспорт.
$logsReady = $false
for ($i=0; $i -lt 12; $i++) {
    $logQuery = '{job="matchmaking-apps"} | json | requestId="'+$chain.RequestId+'" | traceId="'+$chain.TraceId+'"'
    $logs = Invoke-RestMethod ('http://localhost:3100/loki/api/v1/query_range?limit=100&query='+[uri]::EscapeDataString($logQuery))
    $services = @($logs.data.result | ForEach-Object {$_.stream.service} | Sort-Object -Unique)
    if (@('gateway-service','matchmaking-service','rating-service' | Where-Object {$_ -notin $services}).Count -eq 0) {
        $logsReady = $true; break
    }
    Start-Sleep -Seconds 2
}
if (-not $logsReady) { throw 'Loki не получил связанные JSON-логи трех сервисов.' }
Start-Sleep -Seconds 6
foreach ($query in @(
    'http_server_requests_seconds_bucket{job="rating-service",uri="/internal/ratings/{playerId}",le="0.5"}',
    'rating_requests_total{result="success"}',
    'rating_processing_seconds_count{result="success"}',
    'rating_active'
)) {
    if (@(Query-Prom $query).Count -eq 0) { throw "Нет метрики $query" }
}
$exemplarQuery = [uri]::EscapeDataString('http_server_requests_seconds_bucket{job="rating-service",uri="/internal/ratings/{playerId}"}')
$exemplars = Invoke-RestMethod "http://127.0.0.1:9090/api/v1/query_exemplars?query=$exemplarQuery"
$example = $exemplars.data | ForEach-Object {$_.exemplars} |
    Where-Object {$_.labels.trace_id} | Sort-Object timestamp -Descending | Select-Object -First 1
if (-not $example) { throw 'Нет exemplar с trace ID для маршрута рейтинга.' }
# Exemplar – выборочный пример, не обязательно именно запрос из пяти spans выше.
$exemplarTraceId = $example.labels.trace_id
$exampleAvailable = $false
for ($i=0; $i -lt 12; $i++) {
    $trace = Invoke-WebRequest -Uri "http://localhost:3200/api/traces/$exemplarTraceId" `
        -Headers @{Accept='application/json'} -SkipHttpErrorCheck
    if ($trace.StatusCode -eq 200) { $exampleAvailable=$true; break }
    Start-Sleep -Seconds 2
}
if (-not $exampleAvailable) { throw 'Exemplar есть, но соответствующая трасса не найдена в Tempo.' }

$faultResult = 'SKIPPED'
if (-not $SkipFaultExperiment) {
    # Режим ограничен TTL на сервере и дополнительно сбрасывается в finally.
    try {
        $null = & "$PSScriptRoot/set-diagnostic-mode.ps1" -Mode SLOW -Seconds 20
        $null = & "$PSScriptRoot/generate-traffic.ps1" -Count 3 -IntervalMs 0
        $null = & "$PSScriptRoot/set-diagnostic-mode.ps1" -Mode FAIL -Seconds 120
        $firing = $false
        for ($i=0; $i -lt 14; $i++) {
            $null = & "$PSScriptRoot/generate-traffic.ps1" -Count 12 -IntervalMs 100
            Start-Sleep -Seconds 3
            if (Is-Firing) { $firing = $true; break }
        }
        if (-not $firing) { throw 'Правило не перешло в firing при устойчивой доле 503.' }
        $delivered = $false
        for ($i=0; $i -lt 8; $i++) {
            $received = @(Invoke-RestMethod http://localhost:9093/api/v2/alerts)
            if (@($received | Where-Object {$_.labels.alertname -eq 'MatchmakingRatingHighErrorRatio'}).Count) {
                $delivered = $true; break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $delivered) { throw 'Alertmanager не получил firing alert.' }
    } finally {
        $null = & "$PSScriptRoot/set-diagnostic-mode.ps1" -Mode NORMAL
    }
    $recovered = $false
    for ($i=0; $i -lt 18; $i++) {
        $null = & "$PSScriptRoot/generate-traffic.ps1" -Count 5 -IntervalMs 100
        Start-Sleep -Seconds 4
        if (-not (Is-Firing)) { $recovered = $true; break }
    }
    if (-not $recovered) { throw 'Alert не погас после NORMAL и выхода ошибок из окна.' }
    $faultResult = '503 -> firing -> Alertmanager -> NORMAL -> resolved'
}
[pscustomobject]@{
    Status='OK'; Targets=$targets.Count; Dashboard=$dashboard.dashboard.title
    JsonLogServices=($services -join ', '); TraceId=$chain.TraceId
    HttpSpans=$chain.HttpSpans; Exemplar='OK'; ExemplarTraceId=$exemplarTraceId; FaultExperiment=$faultResult
}
