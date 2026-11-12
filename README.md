## Matchmaking Arena

Состояние после практики 8: тайм-ауты, один повтор безопасного чтения и идемпотентная регистрация игрока.

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
Remove-Item Env:RATING_URL -ErrorAction SilentlyContinue
Invoke-RestMethod http://localhost:8085/realms/matchmaking/.well-known/openid-configuration |
  Select-Object issuer
Invoke-WebRequest http://127.0.0.1:3200/ready
Invoke-WebRequest http://127.0.0.1:3100/ready
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
| Toxiproxy TCP / управляющий API | `http://localhost:8666` / `http://127.0.0.1:8474` |
| Метрики BFF / матчмейкинга / рейтингов | `http://localhost:9088/actuator/prometheus` / `http://localhost:9080/actuator/prometheus` / `http://localhost:9083/actuator/prometheus` |
| Grafana | `http://127.0.0.1:3000`, `admin` / `admin-dev-only` |
| Prometheus | `http://127.0.0.1:9090`, страницы `/targets`, `/alerts`, `/rules` |
| Alertmanager | `http://127.0.0.1:9093` |
| Tempo / Loki API | `http://127.0.0.1:3200` / `http://127.0.0.1:3100` |

PostgreSQL из контейнера слушает порт `5433`. Prometheus открывается по `127.0.0.1`, так как на этой машине `[::1]:9090` занят Cockpit из WSL.

## Проверка и эксперимент

~~~powershell
.\mvnw.cmd test
.\scripts\smoke-health.ps1
.\scripts\set-network-fault.ps1 -Mode NORMAL
.\scripts\invoke-rating.ps1 | Format-List
.\scripts\verify-idempotency.ps1 | Format-List
.\scripts\verify-resilience.ps1 | Format-List
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

## Тайм-ауты и повторы

`matchmaking-service` обращается к `rating-service` через прокси `rating`, описанный в `infra/toxiproxy/proxies.json`. Переменная `RATING_URL` по умолчанию указывает на `http://localhost:8666`. Управление Toxiproxy опубликовано только на `127.0.0.1`; скрипты используют User-Agent `sop-lab08`.

| Участок | Ограничение |
| --- | --- |
| BFF: connect / response | 500 мс / 4 с |
| Получение сервисного токена: connect / read | 200 мс / 500 мс, до блока повторов |
| Рейтинг: connect / read | 200 мс / 800 мс |
| Блок повторов / резерв | 2600 мс / 100 мс |
| Попытки | максимум 2, включая исходную |
| Пауза перед повтором | 150 мс с jitter 0,5: от 75 до 225 мс |

Консервативный расчёт: `2 * (200 + 800) + 225 + 100 = 2325 < 2600 мс`; с допуском холодного токена `2600 + 700 = 3300 < 4000 мс`. Остаётся ориентир 700 мс на локальную обработку и передачу. `TimeBudget` допускает новые попытки по остатку времени; это не сквозной дедлайн и не отмена уже выполненной работы.

Повторы выполняет только `RatingRetry`. Разрешены тайм-ауты, отказы и обрывы соединения, HTTP 429/502/503/504. Прочие статусы, ошибки сертификата и неизвестное DNS-имя не повторяются. `Retry-After` поддерживает секунды и HTTP-date. Просьба сервера не сокращается до локального максимума: слишком долгая пауза запрещает повтор по бюджету.

В `matchmaking-service/pom.xml` для `spring-boot:run` и тестовой JVM заданы `jdk.httpclient.disableRetryConnect=true` и `jdk.httpclient.redirects.retrylimit=1`. Они отключают внутренние повторы JDK HttpClient. Для запуска сервиса также задан `java.net.preferIPv4Stack=true`: на этой Windows обход IPv6 localhost превышает лимит 500 мс клиента токенов. Issuer Keycloak остаётся `http://localhost:8085/realms/matchmaking`. При запуске главного класса в IDEA укажите эти три параметра в VM options; при `java -jar` передайте их перед `-jar`. Назначение параметров повторов описано в [документации JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/module-summary.html).

~~~powershell
try {
    .\scripts\set-network-fault.ps1 -Mode LATENCY
    .\scripts\invoke-rating.ps1 | Format-List
} finally {
    .\scripts\set-network-fault.ps1 -Mode NORMAL
}
.\scripts\invoke-rating.ps1 | Format-List
~~~

При задержке downstream на 1300 мс с jitter до 100 мс ожидается 504 после двух попыток. `DISCONNECT` выключает порт прокси и даёт 502. После `NORMAL` ожидается 200. `verify-resilience.ps1` проверяет также два разных ошибочных клиентских span в одной трассе Tempo. События логов: `rating.attempt`, `rating.retry`, `rating.unavailable`. Метрики: `matchmaking_rating_operations_total`, `matchmaking_rating_attempts_total`, `matchmaking_rating_retries_total`. Дашборд Grafana: `Matchmaking Retry Budget`, uid `matchmaking-retry-budget`.

## Идемпотентная регистрация

`POST /api/players/idempotent` требует роль OPERATOR и полный UUID без кавычек в `Idempotency-Key`. Через BFF нужен также CSRF-токен. Тело: `{"nickname":"PlayerDemo","region":"RU"}`. Область ключа: `(principal_id, operation, operation_key)`, где principal получен из проверенного `Principal`, а операция равна `create-player-v1`.

Ник нормализуется через `trim`, отсутствующий регион заменяется на `ZZ`. SHA-256 считается от ника, перевода строки и региона. Миграция V6 создаёт `player_idempotency`; миграции V1–V5 сохраняются. Захват ключа, создание игрока и сохранение полного ответа выполняются в одной транзакции READ COMMITTED. Внешних вызовов в этой операции нет.

| Случай | Результат |
| --- | --- |
| Первый запрос | 201, `Idempotency-Replayed: false` |
| Тот же ключ и нормализованное тело | 201, прежние id/nickname/region, `true` |
| Смена ника или региона со старым ключом | 422 |
| Нет ключа или неверный UUID | 400 |
| Конкурирующая транзакция держит ключ дольше 1 с | 409 |
| Известный ключ старше 24 ч | 410 |
| Другой ключ с уже занятым ником | 409, новая запись ключа откатывается |
| Нет роли / CSRF | Отказ безопасности, ключ его не обходит |

Снимок ответа сохраняется независимо от дальнейшего изменения или удаления игрока. Истёкшие ключи автоматически не удаляются и не освобождаются. Для неизвестного исхода клиент повторяет прежний ключ. Уникальность ника действует независимо от идемпотентности; обычный `POST /api/players` не возвращает сохранённый результат старой команды.

~~~powershell
.\mvnw.cmd -pl matchmaking-service '-Dtest=RatingRetryTest,ServiceTokenClientTest' test
.\mvnw.cmd -pl matchmaking-service -Dtest=PlayerIdempotencyIntegrationTest test
docker compose exec -T postgres psql -U course -d matchmaking -c "SELECT operation, operation_key, state, player_id, response_nickname, response_region FROM player_idempotency ORDER BY created_at DESC LIMIT 5;"
~~~

Интеграционные тесты проверяют параллельные вызовы, откат обеих таблиц и фактическое число записей. Они требуют работающего Docker; `Skipped` из-за его отсутствия не означает успешную проверку PostgreSQL. После завершения остановите Java-сервисы и выполните `docker compose down -v` для очистки данных текущего снимка.
