param([string]$GatewayUrl = 'http://localhost:8088')
$ErrorActionPreference = 'Stop'
$session = & "$PSScriptRoot/open-course-session.ps1" -GatewayUrl $GatewayUrl
$csrf = Invoke-RestMethod -WebSession $session "$GatewayUrl/bff/csrf"
$key = [guid]::NewGuid().ToString()
$name = "Idempotent Player $key"
function Send-Player([string]$Name, [string]$Key, [bool]$IncludeCsrf = $true) {
    $headers = @{}
    if ($Key) { $headers['Idempotency-Key']=$Key }
    if ($IncludeCsrf) { $headers[$csrf.headerName]=$csrf.token }
    try {
        Invoke-WebRequest -WebSession $session -Method Post -Uri "$GatewayUrl/api/players/idempotent" `
            -ContentType 'application/json' -Headers $headers -Body (@{nickname=$Name;region="RU"} | ConvertTo-Json) -SkipHttpErrorCheck
    } finally {
        $null = $session.Headers.Remove('Idempotency-Key')
        $null = $session.Headers.Remove($csrf.headerName)
    }
}
$first = Send-Player $name $key
$repeat = Send-Player $name $key
if ($first.StatusCode -ne 201 -or $repeat.StatusCode -ne 201) { throw 'Первый вызов и повтор должны вернуть 201.' }
if ($repeat.Headers['Idempotency-Replayed'] -ne 'true') { throw 'Повтор не распознан.' }
$a = $first.Content | ConvertFrom-Json
$b = $repeat.Content | ConvertFrom-Json
if ($a.id -ne $b.id -or $a.nickname -ne $b.nickname -or $a.region -ne $b.region) { throw 'Повтор изменил сохранённый результат.' }
if ((Send-Player 'Different player' $key).StatusCode -ne 422) { throw 'Изменённое тело должно дать 422.' }
if ((Send-Player $name '').StatusCode -ne 400) { throw 'Отсутствующий ключ должен дать 400.' }
if ((Send-Player $name 'bad-key').StatusCode -ne 400) { throw 'Некорректный ключ должен дать 400.' }
if ((Send-Player $name $key $false).StatusCode -ne 403) { throw 'Ключ не должен обходить CSRF.' }
$players = Invoke-RestMethod -WebSession $session "$GatewayUrl/api/players"
if (@($players | Where-Object nickname -eq $name).Count -ne 1) { throw 'Создано больше одного игрока.' }
[pscustomobject]@{Status='OK';PlayerId=$a.id;Key=$key;Replayed=$true;Mismatch=422;MissingKey=400;Csrf=403;Effects=1}
