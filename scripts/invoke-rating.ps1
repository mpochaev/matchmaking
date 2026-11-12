param(
    [string]$GatewayUrl = 'http://localhost:8088',
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [string]$PlayerId
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Нужен PowerShell 7.' }
if (-not $Session) { $Session = & "$PSScriptRoot/open-course-session.ps1" -GatewayUrl $GatewayUrl }
if (-not $PlayerId) { $PlayerId = (& "$PSScriptRoot/get-demo-player.ps1" -Session $Session -GatewayUrl $GatewayUrl).id }
$requestId = [guid]::NewGuid().ToString()
$traceId = [guid]::NewGuid().ToString('N')
$watch = [Diagnostics.Stopwatch]::StartNew()
try {
    $response = Invoke-WebRequest -WebSession $Session -Uri "$GatewayUrl/api/players/$PlayerId/rating" `
        -Headers @{'X-Request-ID'=$requestId;traceparent="00-$traceId-00f067aa0ba902b7-01"} -SkipHttpErrorCheck
    $watch.Stop()
    [pscustomobject]@{StatusCode=[int]$response.StatusCode;ElapsedMs=$watch.ElapsedMilliseconds;
        RequestId=$requestId;TraceId=$traceId;PlayerId=$PlayerId;Body=($response.Content | ConvertFrom-Json)}
} finally {
    $null = $Session.Headers.Remove('X-Request-ID')
    $null = $Session.Headers.Remove('traceparent')
}
