package com.romanysrael.battleofbluffs.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateJpaRepository;
import com.romanysrael.battleofbluffs.user.AccountMailSender;
import com.romanysrael.battleofbluffs.user.AccountService;
import com.romanysrael.battleofbluffs.user.AccountService.RegisterAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class PostgresPlatformIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("gotg_test")
            .withUsername("gotg_test")
            .withPassword("gotg_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private MatchApplicationService matches;

    @Autowired
    private MatchAggregateJpaRepository aggregates;

    @Autowired
    private MatchSnapshotCodec codec;

    @MockitoBean
    private AccountMailSender mailSender;

    @Test
    void flywaySchemaPersistsMatchAndReplaysAcceptedCommandAfterRepositoryRestart() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class)).isGreaterThanOrEqualTo(14);

        UUID hostId = accounts.register(new RegisterAccount(
                "persistent_host", "persistent-host@example.test",
                "Strategist!2026", "Persistent Host")).id();
        UUID guestId = accounts.register(new RegisterAccount(
                "persistent_guest", "persistent-guest@example.test",
                "Strategist!2026", "Persistent Guest")).id();
        MatchCommandResult created = matches.createMatch(new CreateMatchCommand(hostId.toString()));
        UUID joinCommandId = UUID.randomUUID();
        JoinMatchCommand join = new JoinMatchCommand(
                joinCommandId, created.view().roomCode(), guestId.toString(), created.version());
        MatchCommandResult accepted = matches.joinMatch(join);

        PostgresMatchRepository restartedRepository = new PostgresMatchRepository(aggregates, codec, jdbc);
        restartedRepository.restorePersistedMatches();
        MatchApplicationService restarted = new MatchApplicationService(
                restartedRepository, new PlayerMatchViewMapper());
        MatchCommandResult replayed = restarted.joinMatch(join);

        assertThat(replayed.version()).isEqualTo(accepted.version());
        assertThat(replayed.view().requestingPlayerId()).isEqualTo(guestId.toString());
        assertThat(restarted.getView(created.view().matchId(), hostId.toString()).playerTwoOccupied())
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_commands WHERE match_id = ? AND command_id = ?",
                Integer.class,
                created.view().matchId(),
                joinCommandId)).isEqualTo(1);
    }
}
