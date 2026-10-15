## Matchmaking Arena

## Состав

- `matchmaking-service` – игроки и лобби в PostgreSQL. Без профиля работает как JWT Resource Server (audience `matchmaking-service`), с профилем `session-auth` – как в работе 2.
- `rating-service` – API рейтингов на порту 8083 с проверкой JWT (работа 3), в основном сценарии не запускается.
- `gateway-service` – API Gateway и Web BFF на порту 8088: вход через Keycloak, сеансы в Redis, Token Relay.
- `infra/keycloak/matchmaking-realm.json` – realm `matchmaking`: клиент `matchmaking-bff`, пользователи `player` и `operator`, машинные клиенты работы 3.

## Запуск

Остановите Java-процессы и Compose предыдущего снимка. В каталоге проекта выполните:

```powershell
docker compose up -d postgres redis keycloak
docker compose ps
Invoke-RestMethod http://localhost:8085/realms/matchmaking/.well-known/openid-configuration |
  Select-Object issuer
.\mvnw.cmd test
```

Последнюю команду `Invoke-RestMethod` повторяйте, пока Keycloak не ответит: gateway требует OIDC metadata уже при запуске. В двух отдельных терминалах из того же каталога запустите:

```powershell
.\mvnw.cmd -pl matchmaking-service spring-boot:run
```

```powershell
.\mvnw.cmd -pl gateway-service spring-boot:run
```

Откройте `http://localhost:8088` и войдите как `operator/operator` либо `player/player`. Роль `player` позволяет читать игроков и лобби и создавать лобби, роль `operator` – также регистрировать игроков. В браузерных DevTools токена в Local Storage быть не должно. `/api/players` и `/api/lobbies` через gateway используют Token Relay; прямой запрос к `http://localhost:8080/api/players` без `Authorization` получает `401`. POST через gateway требует CSRF header, полученный из `/bff/csrf`.

Если порт 8088 уже занят, можно запустить gateway с `$env:SERVER_PORT='18088'` и открыть `http://localhost:18088`: оба точных redirect URI зарегистрированы в realm. Если Docker исчерпал автоматический пул подсетей, только после проверки пересечений с локальными/VPN сетями примените `docker compose -f compose.yaml -f compose.local-network.yaml up -d postgres redis keycloak`. При дальнейшем управлении этим Compose-проектом повторяйте оба `-f`.

Изменения `infra/keycloak/matchmaking-realm.json` подхватываются при пересоздании контейнера Keycloak: `docker compose up -d --force-recreate keycloak`. Не используйте `down -v`, если хотите сохранить данные PostgreSQL и Redis. Для обычной остановки выполните `docker compose down`.
