## Matchmaking Arena

Сервис `matchmaking-service` хранит игроков и лобби в PostgreSQL. Схему базы меняют только миграции Flyway, таблицами `players` и `lobbies` владеет только этот сервис. Вход выполняется через форму, состояние входа хранится в серверном сеансе.

## Запуск

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
.\mvnw.cmd -pl matchmaking-service spring-boot:run "-Dspring-boot.run.profiles=session-auth"
```

Сервис доступен по адресу `http://localhost:8080`. PostgreSQL из контейнера слушает порт `5433`. Страница входа находится по адресу `http://localhost:8080/login`. Учебные пользователи:

- `player/player` с ролью `PLAYER`: чтение игроков и лобби, создание лобби;
- `operator/operator` с ролями `PLAYER` и `OPERATOR`: дополнительно создание игроков и диагностика.

Без входа доступен только `GET /api/public/status`. Диагностика `GET /api/diagnostics` открыта только оператору.

Endpoint `GET /csrf` возвращает CSRF token для PowerShell-сценария и доверенного браузерного клиента. Изменяющие запросы требуют одновременно session cookie, подходящую роль и заголовок `X-XSRF-TOKEN`.

## Проверка

```powershell
.\mvnw.cmd test
docker compose exec postgres psql -U course -d matchmaking -c "select * from flyway_schema_history;"
```

Для остановки инфраструктуры выполните `docker compose down`. Данные сохраняются в именованном томе; команда `docker compose down -v` удалит их и нужна только для осознанного повторения работы с чистой базой.
