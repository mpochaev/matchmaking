package edu.rutmiit.enterprise.matchmaking;

import edu.rutmiit.enterprise.matchmaking.api.ApiException;
import edu.rutmiit.enterprise.matchmaking.api.CreatePlayerRequest;
import edu.rutmiit.enterprise.matchmaking.service.IdempotentPlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "management.server.port=0")
@ActiveProfiles("session-auth")
@Testcontainers(disabledWithoutDocker = true)
class PlayerIdempotencyIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired IdempotentPlayerService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void sameKeyReplaysSameSnapshotWithoutCreatingAnotherPlayer() {
        UUID key = UUID.randomUUID();
        String nickname = "Replay " + key;
        var first = service.create("operator", key, new CreatePlayerRequest(nickname, null));
        var repeat = service.create("operator", key, new CreatePlayerRequest(" " + nickname + " ", "ZZ"));
        assertThat(first.replayed()).isFalse();
        assertThat(repeat.replayed()).isTrue();
        assertThat(repeat.player()).isEqualTo(first.player());
        assertThat(countPlayers(nickname)).isEqualTo(1);
        jdbc.update("update players set nickname = 'Renamed', region = 'EU' where id = ?", first.player().id());
        assertThat(service.create("operator", key, new CreatePlayerRequest(nickname, null)).player()).isEqualTo(first.player());
    }

    @Test
    void changedPayloadIsRejectedWithoutAnotherEffect() {
        UUID key = UUID.randomUUID();
        service.create("operator", key, new CreatePlayerRequest("First " + key, null));
        assertThatThrownBy(() -> service.create("operator", key, new CreatePlayerRequest("Second " + key, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> service.create("operator", key, new CreatePlayerRequest("First " + key, "EU")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThat(countPlayers("Second " + key)).isZero();
    }

    @Test
    void sameKeyFromDifferentPrincipalsDoesNotLeakAnotherResult() {
        UUID key = UUID.randomUUID();
        var first = service.create("operator-a", key, new CreatePlayerRequest("Scoped " + key, null));
        var second = service.create("operator-b", key, new CreatePlayerRequest("Scoped second " + key, null));
        assertThat(second.replayed()).isFalse();
        assertThat(second.player().id()).isNotEqualTo(first.player().id());
    }

    @Test
    void expiredKeyIsNotSilentlyReused() {
        UUID key = UUID.randomUUID();
        String nickname = "Expired " + key;
        service.create("operator", key, new CreatePlayerRequest(nickname, null));
        jdbc.update("update player_idempotency set expires_at = clock_timestamp() - interval '1 second' where operation_key = ?", key);
        assertThatThrownBy(() -> service.create("operator", key, new CreatePlayerRequest(nickname, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
        assertThat(countPlayers(nickname)).isEqualTo(1);
    }

    @Test
    void outerRollbackRemovesBothEffectAndKey() {
        UUID key = UUID.randomUUID();
        String nickname = "Rollback " + key;
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            service.create("operator", key, new CreatePlayerRequest(nickname, null));
            throw new IllegalStateException("Отказ до фиксации");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(countPlayers(nickname)).isZero();
        assertThat(countKeys(key)).isZero();
        assertThat(service.create("operator", key, new CreatePlayerRequest(nickname, null)).replayed()).isFalse();
    }

    @Test
    void databaseErrorDoesNotLeaveAClaimedKey() {
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> service.create("operator", key, new CreatePlayerRequest("x".repeat(101), null)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(countKeys(key)).isZero();
        assertThat(service.create("operator", key, new CreatePlayerRequest("Corrected " + key, null)).replayed()).isFalse();
    }

    @Test
    void differentKeyCannotBypassUniqueNickname() {
        String nickname = "Two intentions " + UUID.randomUUID();
        var first = service.create("operator", UUID.randomUUID(), new CreatePlayerRequest(nickname, null));
        UUID secondKey = UUID.randomUUID();
        assertThatThrownBy(() -> service.create("operator", secondKey, new CreatePlayerRequest(nickname, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(countPlayers(nickname)).isEqualTo(1);
        assertThat(countKeys(secondKey)).isZero();
    }

    @Test
    void waitingForConcurrentOwnerIsBoundedAndDoesNotApplyAnotherEffect() throws Exception {
        UUID key = UUID.randomUUID();
        String nickname = "Lock owner " + key;
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var owner = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = service.create("operator", key, new CreatePlayerRequest(nickname, null));
                claimed.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS)) { throw new IllegalStateException("Тест не освободил владельца"); }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return result;
            }));
            try {
                assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.create("operator", key, new CreatePlayerRequest(nickname, null)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            } finally {
                release.countDown();
            }
            assertThat(owner.get(5, TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(countPlayers(nickname)).isEqualTo(1);
            assertThat(countKeys(key)).isEqualTo(1);
        }
    }

    @Test
    void concurrentDuplicatesCommitOneEffect() throws Exception {
        UUID key = UUID.randomUUID();
        String nickname = "Concurrent " + key;
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return service.create("operator", key, new CreatePlayerRequest(nickname, null)); });
            var second = pool.submit(() -> { start.await(); return service.create("operator", key, new CreatePlayerRequest(nickname, null)); });
            start.countDown();
            var a = first.get(8, TimeUnit.SECONDS);
            var b = second.get(8, TimeUnit.SECONDS);
            assertThat(a.player()).isEqualTo(b.player());
            assertThat(a.replayed()).isNotEqualTo(b.replayed());
            assertThat(countPlayers(nickname)).isEqualTo(1);
            assertThat(countKeys(key)).isEqualTo(1);
        }
    }

    int countPlayers(String nickname) {
        return jdbc.queryForObject("select count(*) from players where nickname = ?", Integer.class, nickname);
    }

    @Test
    void missingPrincipalIsRejectedBeforeAnyEffect() {
        String nickname="Missing subject " + UUID.randomUUID();
        for (String principal : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> service.create(principal, UUID.randomUUID(), new CreatePlayerRequest(nickname, null)))
                    .isInstanceOfSatisfying(ApiException.class, failure ->
                            assertThat(failure.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
        assertThat(countPlayers(nickname)).isZero();
    }

    int countKeys(UUID key) {
        return jdbc.queryForObject("select count(*) from player_idempotency where operation_key = ?", Integer.class, key);
    }
}
