param(
    [Parameter(Mandatory)][Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [string]$GatewayUrl = 'http://localhost:8088'
)
$ErrorActionPreference = 'Stop'
$players = Invoke-RestMethod -WebSession $Session -Uri "$GatewayUrl/api/players"
if ($players.Count -gt 0) { return $players[0] }
$csrf = Invoke-RestMethod -WebSession $Session -Uri "$GatewayUrl/bff/csrf"
try {
    $headers = @{$csrf.headerName=$csrf.token}
    Invoke-RestMethod -WebSession $Session -Method Post -Uri "$GatewayUrl/api/players" `
        -ContentType 'application/json' -Headers $headers -Body '{"nickname":"RetryDemo","region":"RU"}'
} finally {
    $null = $Session.Headers.Remove($csrf.headerName)
}
