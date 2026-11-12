param([string]$GatewayUrl = 'http://localhost:8088')
$ErrorActionPreference = 'Stop'
if (([uri]$GatewayUrl).Host -ne 'localhost' -or ([uri]$GatewayUrl).Scheme -ne 'http') {
    throw 'Помощник предназначен только для локального HTTP localhost.'
}
$browser = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$page = Invoke-WebRequest -WebSession $browser -Uri "$GatewayUrl/oauth2/authorization/keycloak"
$action = [Net.WebUtility]::HtmlDecode([regex]::Match($page.Content,
    '<form id="kc-form-login"[^>]*action="([^"]+)"').Groups[1].Value)
if (-not $action) { throw 'Не найдена форма Keycloak; проверьте готовность realm.' }
if (([uri]$action).Host -ne 'localhost' -or ([uri]$action).Scheme -ne 'http') {
    throw 'Форма Keycloak должна оставаться в локальном HTTP-контуре.'
}
# Только для HTTP localhost: Keycloak помечает часть cookies Secure.
$loginCookies = ($page.Headers['Set-Cookie'] | ForEach-Object { ($_ -split ';')[0] }) -join '; '
$null = Invoke-WebRequest -WebSession $browser -Method Post -Uri $action `
    -Headers @{Cookie=$loginCookies} -ContentType 'application/x-www-form-urlencoded' `
    -Body @{username='operator';password='operator'}
$null = $browser.Headers.Remove('Cookie')
$me = Invoke-RestMethod -WebSession $browser -Uri "$GatewayUrl/bff/me"
if ($me.name -ne 'operator') { throw 'Вход редактора не завершился.' }
$browser
