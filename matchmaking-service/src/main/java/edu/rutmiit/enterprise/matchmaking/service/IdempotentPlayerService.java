package edu.rutmiit.enterprise.matchmaking.service;

import edu.rutmiit.enterprise.matchmaking.api.ApiException;
import edu.rutmiit.enterprise.matchmaking.api.PlayerResponse;
import edu.rutmiit.enterprise.matchmaking.api.CreatePlayerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.sql.SQLException;

@Service
public class IdempotentPlayerService {
    private static final String OPERATION = "create-player-v1";
    private final JdbcTemplate jdbc;
    private final MatchmakingService matchmaking;

    public IdempotentPlayerService(JdbcTemplate jdbc, MatchmakingService matchmaking) {
        this.jdbc = jdbc;
        this.matchmaking = matchmaking;
    }

    public record Result(PlayerResponse player, boolean replayed) {}
    private record Stored(String fingerprint, String state, UUID playerId, String nickname, String region, boolean expired) {}

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 3)
    public Result create(String principal, UUID key, CreatePlayerRequest request) {
        if (principal == null || principal.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "В токене отсутствует идентификатор пользователя");
        }
        // Ожидание конкурирующей транзакции ограничено и не превращается в зависший запрос.
        jdbc.execute("SET LOCAL lock_timeout = '1000ms'");
        jdbc.execute("SET LOCAL statement_timeout = '2000ms'");
        String nickname = request.nickname().trim();
        String region = request.region() == null ? "ZZ" : request.region();
        String fingerprint = fingerprint(nickname + "\n" + region);
        int claimed;
        try {
            claimed = jdbc.update("""
                INSERT INTO player_idempotency(principal_id, operation, operation_key, request_fingerprint, state)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS') ON CONFLICT DO NOTHING
                """, principal, OPERATION, key, fingerprint);
        } catch (DataAccessException failure) {
            // Классы-обёртки зависят от translator; PostgreSQL SQLSTATE устойчивее для этого случая.
            if (failure.getMostSpecificCause() instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                throw new ApiException(HttpStatus.CONFLICT, "Конкурирующая команда ещё выполняется; повторите тот же ключ позже");
            }
            throw failure;
        }
        if (claimed == 0) {
            // Это отдельная команда: READ COMMITTED видит уже зафиксированную запись победителя.
            Stored stored = jdbc.queryForObject("""
                    SELECT request_fingerprint, state, player_id, response_nickname, response_region,
                           expires_at <= clock_timestamp() AS expired
                    FROM player_idempotency WHERE principal_id = ? AND operation = ? AND operation_key = ?
                    """, (row, index) -> new Stored(row.getString("request_fingerprint"), row.getString("state"),
                    row.getObject("player_id", UUID.class), row.getString("response_nickname"), row.getString("response_region"), row.getBoolean("expired")),
                    principal, OPERATION, key);
            if (stored == null) { throw new IllegalStateException("Запись ключа не найдена после конфликта"); }
            if (stored.expired()) {
                throw new ApiException(HttpStatus.GONE, "Срок ключа истёк; сначала согласуйте результат операции");
            }
            if (!stored.fingerprint().equals(fingerprint)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Ключ уже использован с другими данными игрока");
            }
            if (!stored.state().equals("COMPLETED")) {
                throw new ApiException(HttpStatus.CONFLICT, "Команда с этим ключом ещё выполняется");
            }
            return new Result(new PlayerResponse(stored.playerId(), stored.nickname(), stored.region()), true);
        }

        // REQUIRED у MatchmakingService присоединяется к этой же локальной транзакции PostgreSQL.
        PlayerResponse player = matchmaking.createPlayer(new CreatePlayerRequest(nickname, region));
        int completed = jdbc.update("""
                UPDATE player_idempotency SET state = 'COMPLETED', player_id = ?, response_nickname = ?, response_region = ?
                WHERE principal_id = ? AND operation = ? AND operation_key = ?
                """, player.id(), player.nickname(), player.region(), principal, OPERATION, key);
        if (completed != 1) { throw new IllegalStateException("Не удалось сохранить результат команды"); }
        return new Result(player, false);
    }

    static String fingerprint(String nickname) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(nickname.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK обязан предоставлять SHA-256", impossible);
        }
    }
}
