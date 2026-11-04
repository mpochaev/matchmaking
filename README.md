## Matchmaking Arena

## Состав

- `matchmaking-service` – игроки и лобби в PostgreSQL. Без профиля работает как JWT Resource Server (audience `matchmaking-service`), с профилем `session-auth` – как в работе 2. Адрес `GET /api/players/{id}/rating` запрашивает рейтинг у `rating-service` с сервисным токеном.
- `rating-service` – API рейтингов на порту 8083 с проверкой JWT, прикладными метриками и управляемыми режимами сбоя `NORMAL`, `SLOW`, `FAIL`.
- `gateway-service` – API Gateway и Web BFF на порту 8088: вход через Keycloak, сеансы в Redis, Token Relay.
- `infra/keycloak/matchmaking-realm.json` – realm `matchmaking`.
- `infra/otel`, `infra/tempo` – трассы; `infra/prometheus`, `infra/alertmanager` – метрики, правила и алерты; `infra/alloy`, `infra/loki` – JSON-логи; `infra/grafana` – источники данных и дашборд `Matchmaking Golden Signals`.

Каждый сервис отдаёт метрики Prometheus на служебном порту 127.0.0.1 (9080, 9083, 9088), пишет ECS JSON-логи в `runtime-logs` и передаёт `X-Request-ID` и W3C Trace Context.

## Запуск

Нужны JDK 21+, PowerShell 7 и Docker Desktop с Linux-контейнерами. Остановите приложения и Compose прежнего снимка. В корне проекта выполните:

~~~powershell
docker compose up -d
docker compose ps
Invoke-RestMethod http://localhost:8085/realms/matchmaking/.well-known/openid-configuration |
  Select-Object issuer
Invoke-WebRequest http://localhost:3200/ready
Invoke-WebRequest http://localhost:3100/ready
.\mvnw.cmd -DskipTests install
~~~

Временный `503` от `/ready` при старте допустим: повторите проверку через 15–20 секунд. В трёх терминалах из корня проекта запустите:

~~~powershell
.\mvnw.cmd -pl rating-service spring-boot:run
~~~

~~~powershell
.\mvnw.cmd -pl matchmaking-service spring-boot:run
~~~

~~~powershell
.\mvnw.cmd -pl gateway-service spring-boot:run
~~~

| Компонент | Адрес |
| --- | --- |
| BFF | `http://localhost:8088`, пользователи Keycloak `operator/operator`, `player/player` |
| Матчмейкинг / рейтинги | `http://localhost:8080` / `http://localhost:8083` |
| Метрики BFF / матчмейкинга / рейтингов | `http://localhost:9088/actuator/prometheus` / `http://localhost:9080/actuator/prometheus` / `http://localhost:9083/actuator/prometheus` |
| Grafana | `http://localhost:3000`, `admin` / `admin-dev-only` |
| Prometheus | `http://127.0.0.1:9090`, страницы `/targets`, `/alerts`, `/rules` |
| Alertmanager | `http://localhost:9093` |
| Tempo / Loki API | `http://localhost:3200` / `http://localhost:3100` |

PostgreSQL из контейнера слушает порт `5433`. Prometheus открывается по `127.0.0.1`, так как на этой машине `[::1]:9090` занят Cockpit из WSL.

## Проверка и эксперимент

~~~powershell
.\mvnw.cmd test
.\scripts\smoke-health.ps1
.\scripts\verify-tracing.ps1 | Format-List
.\scripts\verify-observability.ps1 | Format-List
~~~

Правила можно проверить независимо на искусственных рядах:

~~~powershell
docker compose run --rm --no-deps --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose run --rm --no-deps -w /etc/prometheus --entrypoint promtool prometheus test rules rules-test.yml
~~~

Пример поиска отказов в Loki:

~~~logql
{job="matchmaking-apps",service="rating-service"} | json | outcome="failure"
~~~
