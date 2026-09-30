## Matchmaking Arena

## Состав

- `matchmaking-service` – игроки и лобби в PostgreSQL, вход через форму и серверный сеанс (состояние работы 2).
- `rating-service` – API рейтингов на порту 8083 с проверкой JWT и ролей.
- `infra/keycloak/matchmaking-realm.json` – realm `matchmaking` с тремя машинными клиентами и ролями `service`, `operator`, `player`.

## Запуск основного сценария

Нужны JDK 21+, PowerShell 7 и Docker Desktop с Linux-контейнерами.

```powershell
docker compose up -d keycloak
docker compose logs keycloak
Invoke-RestMethod http://localhost:8085/realms/matchmaking/.well-known/openid-configuration |
  Select-Object issuer,token_endpoint,jwks_uri
.\mvnw.cmd -pl rating-service spring-boot:run
```

Keycloak доступен по адресу `http://localhost:8085`, консоль администратора `http://localhost:8085/admin` (`admin` / `admin-dev-only`, только локально). Адреса `rating-service`:

- `GET /internal/ratings/{playerId}` – для фонового сервиса, роль `service`;
- `GET /internal/admin/info` – служебный, роль `operator`;
- `GET /api/ranks` – пользовательский, роли `player` и `operator`.

Машинные клиенты realm:

| Клиент | Секрет | Роль | Получатель токена (aud) |
|---|---|---|---|
| `matchmaking-service-client` | `matchmaking-service-secret` | `service` | `rating-service` |
| `matchmaking-ops-client` | `matchmaking-ops-secret` | `operator` | `rating-service` |
| `matchmaking-other-client` | `matchmaking-other-secret` | `service` | `other-api` |

## Сервис матчмейкинга

```powershell
docker compose up -d postgres
.\mvnw.cmd -pl matchmaking-service spring-boot:run "-Dspring-boot.run.profiles=session-auth"
```

Его пользователи `player` и `operator` хранятся в памяти приложения и с Keycloak не связаны. PostgreSQL из контейнера слушает порт `5433`.

## Изменение realm и остановка

При обычном повторном старте существующий realm не перезаписывается. После изменения JSON:

```powershell
docker compose up -d --force-recreate keycloak
```

Проверка: `.\mvnw.cmd test`. Остановка: `docker compose down`.
