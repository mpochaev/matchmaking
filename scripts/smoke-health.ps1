param([string]$GatewayUrl = 'http://localhost:8088')
$ErrorActionPreference = 'Stop'
$urls = @(
    "$GatewayUrl/livez", "$GatewayUrl/readyz",
    'http://localhost:8080/livez', 'http://localhost:8080/readyz',
    'http://localhost:8083/livez', 'http://localhost:8083/readyz',
    'http://localhost:9088/actuator/prometheus',
    'http://localhost:9080/actuator/prometheus',
    'http://localhost:9083/actuator/prometheus',
    'http://127.0.0.1:9090/-/ready', 'http://127.0.0.1:9093/-/ready',
    'http://127.0.0.1:3100/ready', 'http://127.0.0.1:3200/ready',
    'http://127.0.0.1:3000/api/health'
)
foreach ($url in $urls) {
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 10
    if ($response.StatusCode -ne 200) { throw "Не готов: $url" }
    [pscustomobject]@{Url=$url; Status=[int]$response.StatusCode}
}
