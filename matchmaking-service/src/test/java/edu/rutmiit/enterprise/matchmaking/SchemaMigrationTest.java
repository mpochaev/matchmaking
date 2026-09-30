package edu.rutmiit.enterprise.matchmaking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6-alpine");

    @Test
    void laterMigrationBackfillsRowsCreatedByTheEarlierSchema() throws Exception {
        Flyway upToVersionTwo = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load();

        assertThat(upToVersionTwo.migrate().migrationsExecuted).isEqualTo(2);
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            UUID hostId = UUID.randomUUID();
            try (var player = connection.prepareStatement(
                    "insert into players(id, nickname) values (?, ?)")) {
                player.setObject(1, hostId);
                player.setString(2, "Игрок до миграции V3");
                player.executeUpdate();
            }
            try (var lobby = connection.prepareStatement(
                    "insert into lobbies(id, name, code, host_id, status, max_players) " +
                            "values (?, ?, ?, ?, ?, ?)")) {
                lobby.setObject(1, UUID.randomUUID());
                lobby.setString(2, "Лобби до миграции V3");
                lobby.setString(3, "ABC123");
                lobby.setObject(4, hostId);
                lobby.setString(5, "WAITING");
                lobby.setInt(6, 5);
                lobby.executeUpdate();
            }
        }

        Flyway upToVersionThree = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load();
        assertThat(upToVersionThree.migrate().migrationsExecuted).isOne();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement(
                     "select max_players, created_at from lobbies where code = ?")) {
            statement.setString(1, "ABC123");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("max_players")).isEqualTo(5);
                assertThat(result.getObject("created_at")).isNotNull();
            }
        }
    }
}
