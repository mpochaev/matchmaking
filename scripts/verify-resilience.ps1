param([string]$GatewayUrl = 'http://localhost:8088')
$ErrorActionPreference = 'Stop'
$foreignToxics = @((Invoke-RestMethod http://127.0.0.1:8474/proxies/rating -UserAgent 'sop-lab08').toxics |
    Where-Object name -ne 'matchmaking-lab08-latency')
if ($foreignToxics.Count) { throw 'В proxy есть чужие toxics. Уточните их назначение до проверки.' }
$session = & "$PSScriptRoot/open-course-session.ps1" -GatewayUrl $GatewayUrl
$player = & "$PSScriptRoot/get-demo-player.ps1" -Session $session -GatewayUrl $GatewayUrl
function Call-Journey {
    & "$PSScriptRoot/invoke-rating.ps1" -GatewayUrl $GatewayUrl -Session $session -PlayerId $player.id
}
function Assert-Attempts($Journey, [int]$Expected) {
    $file = Join-Path (Split-Path $PSScriptRoot -Parent) 'runtime-logs/matchmaking-service.log'
    $events = @(Get-Content -LiteralPath $file | ForEach-Object {$_ | ConvertFrom-Json} |
        Where-Object {$_.requestId -eq $Journey.RequestId -and $_.event -eq 'rating.attempt'})
    if ($events.Count -ne $Expected) { throw "Для $($Journey.RequestId) ожидалось $Expected попыток, получено $($events.Count)." }
}
$null = & "$PSScriptRoot/set-diagnostic-mode.ps1" -Mode NORMAL
$null = & "$PSScriptRoot/set-network-fault.ps1" -Mode NORMAL
try {
    $normal = Call-Journey
    if ($normal.StatusCode -ne 200) { throw 'Исходный путь не работает.' }
    Assert-Attempts $normal 1
    $null = & "$PSScriptRoot/set-network-fault.ps1" -Mode LATENCY
    $slow = Call-Journey
    if ($slow.StatusCode -ne 504) { throw "Ожидался тайм-аут 504, получен $($slow.StatusCode)." }
    Assert-Attempts $slow 2
    $null = & "$PSScriptRoot/set-network-fault.ps1" -Mode DISCONNECT
    $disconnected = Call-Journey
    if ($disconnected.StatusCode -ne 502) { throw "Ожидался отказ соединения 502, получен $($disconnected.StatusCode)." }
    Assert-Attempts $disconnected 2
} finally {
    $null = & "$PSScriptRoot/set-network-fault.ps1" -Mode NORMAL
}
$recovered = Call-Journey
if ($recovered.StatusCode -ne 200) { throw 'Путь не восстановился.' }
Assert-Attempts $recovered 1

# Прозрачный TCP-proxy не создаёт spans; у matchmaking-service два HTTP client spans одной трассы.
$attempts = @()
for ($i=0; $i -lt 15; $i++) {
    $response = Invoke-WebRequest "http://127.0.0.1:3200/api/traces/$($slow.TraceId)" `
        -Headers @{Accept='application/json'} -SkipHttpErrorCheck
    if ($response.StatusCode -eq 200) {
        $trace = $response.Content | ConvertFrom-Json
        $attempts = @(foreach ($batch in (@($trace.batches) + @($trace.resourceSpans))) {
            $service = ($batch.resource.attributes | Where-Object key -eq 'service.name').value.stringValue
            if ($service -eq 'matchmaking-service') {
                foreach($scope in (@($batch.scopeSpans)+@($batch.instrumentationLibrarySpans))) {
                    $scope.spans | Where-Object {$_.kind -eq 'SPAN_KIND_CLIENT' -and $_.name -match '(?i)^http '}
                }
            }
        })
        if ($attempts.Count -eq 2) { break }
    }
    Start-Sleep -Seconds 2
}
if ($attempts.Count -ne 2 -or @($attempts.spanId | Sort-Object -Unique).Count -ne 2) {
    throw 'Tempo не подтвердил два разных HTTP client spans.'
}
if (@($attempts | Where-Object {$_.status.code -eq 'STATUS_CODE_ERROR'}).Count -ne 2) {
    throw 'Тайм-ауты не отмечены ошибками в трассе.'
}
[pscustomobject]@{Status='OK';Baseline=$normal.StatusCode;Timeout=$slow.StatusCode;TimeoutMs=$slow.ElapsedMs;
    Attempts=2;ConnectionFailure=$disconnected.StatusCode;Recovered=$recovered.StatusCode;TraceId=$slow.TraceId}
