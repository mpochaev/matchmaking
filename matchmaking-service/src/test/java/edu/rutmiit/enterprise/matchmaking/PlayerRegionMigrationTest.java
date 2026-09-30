package edu.rutmiit.enterprise.matchmaking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.Types;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PlayerRegionMigrationTest {
    private static final String NICKNAME = "Игрок до миграции V5";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6-alpine");

    @Test
    void versionFiveBackfillsRegionForPlayersCreatedBeforeIt() throws Exception {
        Flyway upToVersionFour = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load();

        assertThat(upToVersionFour.migrate().migrationsExecuted).isEqualTo(4);

        UUID existingId = insertPlayerBeforeVersionFive();

        Flyway allMigrations = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(allMigrations.migrate().migrationsExecuted).isOne();

        assertPlayerWasPreservedAndBackfilled(existingId);
    }

    private UUID insertPlayerBeforeVersionFive() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var player = connection.prepareStatement(
                    "insert into players(id, nickname, region) values (?, ?, ?)")) {
                player.setObject(1, playerId);
                player.setString(2, NICKNAME);
                player.setNull(3, Types.VARCHAR);
                player.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "select nickname, region from players where id = ?")) {
                statement.setObject(1, playerId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("nickname")).isEqualTo(NICKNAME);
                    assertThat(result.getString("region")).isNull();
                }
            }
        }
        return playerId;
    }

    private void assertPlayerWasPreservedAndBackfilled(UUID playerId) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.prepareStatement(
                    "select nickname, region from players where id = ?")) {
                statement.setObject(1, playerId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("nickname")).isEqualTo(NICKNAME);
                    assertThat(result.getString("region")).isEqualTo("ZZ");
                }
            }
            try (var statement = connection.prepareStatement(
                    "select count(*) from flyway_schema_history where success and version is not null");
                 var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(5);
            }
        }
    }
}
