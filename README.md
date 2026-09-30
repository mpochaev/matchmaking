## Matchmaking Arena

Сервис `matchmaking-service` хранит игроков и лобби в PostgreSQL. Схему базы меняют только миграции Flyway, таблицами `players` и `lobbies` владеет только этот сервис.

## Запуск

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
.\mvnw.cmd -pl matchmaking-service spring-boot:run
```

Сервис доступен по адресу `http://localhost:8080`. PostgreSQL из контейнера слушает порт `5433`. Основные ресурсы:

- `GET /api/players` и `POST /api/players`;
- `GET /api/lobbies`, `GET /api/lobbies/{id}` и `POST /api/lobbies`.

## Проверка

```powershell
.\mvnw.cmd test
docker compose exec postgres psql -U course -d matchmaking -c "select * from flyway_schema_history;"
```

Для остановки инфраструктуры выполните `docker compose down`. Данные сохраняются в именованном томе; команда `docker compose down -v` удалит их и нужна только для осознанного повторения работы с чистой базой.
