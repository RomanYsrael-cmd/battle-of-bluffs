package com.romanysrael.battleofbluffs.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.romanysrael.battleofbluffs.competition.PlayerRatingEntity.PlayerRatingId;
import com.romanysrael.battleofbluffs.competition.PlayerRatingRepository;
import com.romanysrael.battleofbluffs.competition.RatingChangeRepository;
import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.Commands.CancelMatchCommand;
import com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.MatchSummary;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateEntity;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateJpaRepository;
import com.romanysrael.battleofbluffs.user.AccountMailSender;
import com.romanysrael.battleofbluffs.user.AccountService;
import com.romanysrael.battleofbluffs.user.AccountService.RegisterAccount;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    private RatingService ratingService;

    @Autowired
    private PlayerRatingRepository playerRatings;

    @Autowired
    private RatingChangeRepository ratingChanges;

    @MockitoBean
    private AccountMailSender mailSender;

    @Test
    void flywaySchemaPersistsMatchAndReplaysLifecycleCommandsAfterRepositoryRestart() {
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

        CancelMatchCommand cancel = new CancelMatchCommand(
                UUID.randomUUID(),
                created.view().matchId(),
                hostId.toString(),
                accepted.version());
        MatchLifecycleResult cancelled = restarted.cancelMatch(cancel);
        PostgresMatchRepository cancelledRepository = new PostgresMatchRepository(
                aggregates, codec, jdbc);
        cancelledRepository.restorePersistedMatches();
        MatchApplicationService afterCancellation = new MatchApplicationService(
                cancelledRepository, new PlayerMatchViewMapper());

        assertThat(afterCancellation.cancelMatch(cancel)).isEqualTo(cancelled);
        assertThat(afterCancellation.currentMatches(hostId.toString())).isEmpty();
        assertThat(afterCancellation.summary(created.view().matchId()).terminalResult().reason())
                .isEqualTo(com.romanysrael.battleofbluffs.game.domain.TerminalReason.ROOM_CANCELLED);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_commands WHERE match_id = ? AND command_id = ?",
                Integer.class,
                created.view().matchId(),
                cancel.commandId())).isEqualTo(1);
    }

    @Test
    void concurrentRatingFinalizationAppliesOneLedgerAndOneRatingChangePerPlayer() throws Exception {
        UUID playerOneId = accounts.register(new RegisterAccount(
                "rating_player_one", "rating-one@example.test",
                "Strategist!2026", "Rating Player One")).id();
        UUID playerTwoId = accounts.register(new RegisterAccount(
                "rating_player_two", "rating-two@example.test",
                "Strategist!2026", "Rating Player Two")).id();
        UUID matchId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        aggregates.saveAndFlush(new MatchAggregateEntity(
                matchId,
                null,
                MatchMode.RANKED.name(),
                TimerMode.STANDARD_15_PLUS_5.name(),
                MatchPhase.TERMINAL.name(),
                1,
                "{}",
                now,
                true));
        MatchSummary summary = new MatchSummary(
                matchId,
                MatchMode.RANKED,
                TimerMode.STANDARD_15_PLUS_5,
                MatchPhase.TERMINAL,
                Map.of(
                        PlayerSide.PLAYER_ONE, playerOneId.toString(),
                        PlayerSide.PLAYER_TWO, playerTwoId.toString()),
                TerminalResult.win(PlayerSide.PLAYER_ONE, TerminalReason.RESIGNATION),
                1);

        runConcurrently(() -> ratingService.apply(summary));

        UUID seasonId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        assertThat(ratingChanges.findByMatchIdAndUserId(matchId, playerOneId)).isPresent();
        assertThat(ratingChanges.findByMatchIdAndUserId(matchId, playerTwoId)).isPresent();
        assertThat(playerRatings.findById(new PlayerRatingId(playerOneId, seasonId)))
                .get()
                .extracting(rating -> rating.getRatedGames())
                .isEqualTo(1);
        assertThat(playerRatings.findById(new PlayerRatingId(playerTwoId, seasonId)))
                .get()
                .extracting(rating -> rating.getRatedGames())
                .isEqualTo(1);
    }

    private static void runConcurrently(Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> runWhenReleased(action, ready, start));
            Future<?> second = executor.submit(() -> runWhenReleased(action, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runWhenReleased(
            Runnable action,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent rating test did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent rating test was interrupted", exception);
        }
        action.run();
    }
}
