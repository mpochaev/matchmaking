## Matchmaking Arena

## Состав

- `matchmaking-service` – игроки и лобби в PostgreSQL. Без профиля работает как JWT Resource Server (audience `matchmaking-service`), с профилем `session-auth` – как в работе 2. Адрес `GET /api/players/{id}/rating` запрашивает рейтинг у `rating-service` с сервисным токеном.
- `rating-service` – API рейтингов на порту 8083 с проверкой JWT.
- `gateway-service` – API Gateway и Web BFF на порту 8088: вход через Keycloak, сеансы в Redis, Token Relay.
- `infra/keycloak/matchmaking-realm.json` – realm `matchmaking`.
- `infra/otel`, `infra/tempo`, `infra/grafana` – сбор и просмотр трасс.

Все три сервиса передают `X-Request-ID` и W3C Trace Context и экспортируют трассы по OTLP в OpenTelemetry Collector.

## Запуск

Нужны JDK 21+, PowerShell 7 и Docker Desktop с Linux-контейнерами. Остановите приложения и Compose прежнего снимка. В корне проекта выполните:

~~~powershell
docker compose up -d
docker compose ps
Invoke-RestMethod http://localhost:8085/realms/matchmaking/.well-known/openid-configuration |
  Select-Object issuer
Invoke-WebRequest http://localhost:3200/ready
.\mvnw.cmd -DskipTests install
~~~

При первом запуске дождитесь готовности Keycloak и Tempo. В трёх терминалах из корня проекта запустите:

~~~powershell
.\mvnw.cmd -pl rating-service spring-boot:run
~~~

~~~powershell
.\mvnw.cmd -pl matchmaking-service spring-boot:run
~~~

~~~powershell
.\mvnw.cmd -pl gateway-service spring-boot:run
~~~

Шлюз: `http://localhost:8088` (`operator/operator`, `player/player`). Grafana: `http://localhost:3000` (`admin/admin-dev-only`), источник данных Tempo настроен. PostgreSQL из контейнера слушает порт `5433`.

## Проверка

~~~powershell
.\mvnw.cmd test
.\scripts\verify-tracing.ps1 | Format-List
~~~

Файлы логов находятся в `runtime-logs` в корне проекта при запуске через Maven Wrapper.
