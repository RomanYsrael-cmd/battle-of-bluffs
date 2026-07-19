package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.FormationPiece;
import static com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.LockFormationCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.MakeMoveCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.ResignCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.SubmitFormationCommand;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.MatchState;
import com.romanysrael.battleofbluffs.game.domain.PendingFlagChallenge;
import com.romanysrael.battleofbluffs.game.domain.Piece;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MatchTimingAndPresenceTest {
    private static final String PLAYER_ONE = "10000000-0000-4000-8000-000000000001";
    private static final String PLAYER_TWO = "20000000-0000-4000-8000-000000000002";

    private MutableClock clock;
    private InMemoryMatchRepository repository;
    private PlayerMatchViewMapper mapper;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-19T10:00:00Z"));
        repository = new InMemoryMatchRepository();
        mapper = new PlayerMatchViewMapper();
        mapper.setClock(clock);
        service = new MatchApplicationService(repository, mapper);
        service.setClock(clock);
    }

    @Test
    void setupDeadlineAwardsTheOnlyLockedPlayer() {
        Fixture fixture = occupied(MatchMode.CASUAL, TimerMode.CASUAL_UNTIMED);
        long version = view(fixture).version();
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), PLAYER_ONE, version,
                formation(PlayerSide.PLAYER_ONE)));
        version = view(fixture).version();
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), PLAYER_ONE, version));

        clock.advance(MatchTimingRules.FORMATION_LIMIT);
        service.evaluateAllDeadlines();

        PlayerMatchView terminal = view(fixture);
        assertEquals(MatchPhase.TERMINAL, terminal.phase());
        assertEquals(PlayerSide.PLAYER_ONE, terminal.terminalResult().winner());
        assertEquals(TerminalReason.SETUP_TIMEOUT, terminal.terminalResult().reason());
    }

    @Test
    void setupDeadlineWithNeitherFormationLockedIsNoContest() {
        Fixture fixture = occupied(MatchMode.CASUAL, TimerMode.CASUAL_UNTIMED);

        clock.advance(MatchTimingRules.FORMATION_LIMIT);
        service.evaluateAllDeadlines();

        assertEquals(TerminalReason.NO_CONTEST, view(fixture).terminalResult().reason());
    }

    @Test
    void moveAcceptedImmediatelyBeforeDeadlineGetsIncrementAndStartsOnlyOpponentClock() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PlayerMatchView before = view(fixture);
        PlayerSide mover = before.currentPlayer();
        clock.advance(MatchTimingRules.INITIAL_PLAY_TIME.minusMillis(1));

        MatchCommandResult moved = service.makeMove(legalMove(fixture, before));

        long moverRemaining = mover == PlayerSide.PLAYER_ONE
                ? moved.view().timer().playerOneRemainingMillis()
                : moved.view().timer().playerTwoRemainingMillis();
        long opponentRemaining = mover == PlayerSide.PLAYER_ONE
                ? moved.view().timer().playerTwoRemainingMillis()
                : moved.view().timer().playerOneRemainingMillis();
        assertEquals(5_001, moverRemaining);
        assertEquals(MatchTimingRules.INITIAL_PLAY_TIME.toMillis(), opponentRemaining);
        assertEquals(mover.opponent(), moved.view().currentPlayer());
    }

    @Test
    void rejectedCommandDoesNotRestoreElapsedClockTimeOrChangeVersion() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PlayerMatchView before = view(fixture);
        clock.advance(Duration.ofSeconds(10));
        PlayerSide mover = before.currentPlayer();
        Position source = mover == PlayerSide.PLAYER_ONE
                ? new Position(2, 0)
                : new Position(5, 0);

        assertThrows(MatchApplicationException.class, () -> service.makeMove(new MakeMoveCommand(
                UUID.randomUUID(), fixture.matchId(), player(mover), before.version(),
                source, new Position(source.row(), source.column() + 2))));

        PlayerMatchView after = view(fixture);
        assertEquals(before.version(), after.version());
        long remaining = mover == PlayerSide.PLAYER_ONE
                ? after.timer().playerOneRemainingMillis()
                : after.timer().playerTwoRemainingMillis();
        assertEquals(MatchTimingRules.INITIAL_PLAY_TIME.minusSeconds(10).toMillis(), remaining);
    }

    @Test
    void commandAtDeadlineLosesByTimeoutAndCannotCreateDuplicateTerminalOutcome() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PlayerMatchView before = view(fixture);
        clock.advance(MatchTimingRules.INITIAL_PLAY_TIME);

        MatchApplicationException failure = assertThrows(
                MatchApplicationException.class,
                () -> service.makeMove(legalMove(fixture, before)));

        assertEquals(MatchErrorCode.TERMINAL_MATCH, failure.code());
        PlayerMatchView terminal = view(fixture);
        assertEquals(TerminalReason.TIMEOUT, terminal.terminalResult().reason());
        long terminalVersion = terminal.version();
        service.evaluateAllDeadlines();
        service.evaluateAllDeadlines();
        assertEquals(terminalVersion, view(fixture).version());
    }

    @Test
    void resignationReceivedBeforeDeadlineWinsTheRaceAgainstTimeout() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PlayerMatchView before = view(fixture);
        clock.advance(MatchTimingRules.INITIAL_PLAY_TIME.minusMillis(1));

        service.resign(new ResignCommand(
                UUID.randomUUID(), fixture.matchId(), player(before.currentPlayer()), before.version()));

        assertEquals(TerminalReason.RESIGNATION, view(fixture).terminalResult().reason());
    }

    @Test
    void pendingFlagChallengeStillEndsByAuthoritativeTimeout() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PrivateMatch match = repository.findById(fixture.matchId()).orElseThrow();
        synchronized (match) {
            Piece flag = match.state.board().pieces().stream()
                    .filter(piece -> piece.rank() == Rank.FLAG)
                    .findFirst()
                    .orElseThrow();
            PlayerSide responder = flag.owner().opponent();
            Piece challenger = match.state.board().pieces().stream()
                    .filter(piece -> piece.owner() == responder)
                    .findFirst()
                    .orElseThrow();
            PendingFlagChallenge challenge = new PendingFlagChallenge(
                    flag.id(), flag.position().orElseThrow(), responder, Set.of(challenger.id()));
            match.state = MatchState.active(
                    match.state.board(), responder, match.state.acceptedMoveCount(), challenge);
            match.remainingMillis.put(responder, MatchTimingRules.INITIAL_PLAY_TIME.toMillis());
            match.turnStartedAt = clock.instant();
            match.turnDeadline = clock.instant().plus(MatchTimingRules.INITIAL_PLAY_TIME);
        }

        clock.advance(MatchTimingRules.INITIAL_PLAY_TIME);
        service.evaluateAllDeadlines();

        assertEquals(TerminalReason.TIMEOUT, view(fixture).terminalResult().reason());
    }

    @Test
    void activeClockContinuesDuringDisconnectAndEarlierTimeoutWins() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PrivateMatch match = repository.findById(fixture.matchId()).orElseThrow();
        PlayerSide activeSide = match.state.currentPlayer().orElseThrow();
        synchronized (match) {
            match.remainingMillis.put(activeSide, 30_000L);
            match.turnStartedAt = clock.instant();
            match.turnDeadline = clock.instant().plusSeconds(30);
        }
        service.playerDisconnected(fixture.matchId(), player(activeSide));

        clock.advance(Duration.ofSeconds(30));
        service.evaluateAllDeadlines();

        assertEquals(TerminalReason.TIMEOUT, view(fixture).terminalResult().reason());
    }

    @Test
    void dualDisconnectWaitsForBothGracePeriodsThenEndsNoContest() {
        Fixture fixture = activeUntimedMatch();
        service.playerDisconnected(fixture.matchId(), PLAYER_ONE);
        clock.advance(Duration.ofSeconds(10));
        service.playerDisconnected(fixture.matchId(), PLAYER_TWO);

        clock.advance(Duration.ofSeconds(50));
        service.evaluateAllDeadlines();
        assertEquals(MatchPhase.ACTIVE, view(fixture).phase());

        clock.advance(Duration.ofSeconds(10));
        service.evaluateAllDeadlines();
        assertEquals(TerminalReason.NO_CONTEST, view(fixture).terminalResult().reason());
    }

    @Test
    void reconnectingPlayerLeavesExpiredOpponentGraceToForfeit() {
        Fixture fixture = activeUntimedMatch();
        service.playerDisconnected(fixture.matchId(), PLAYER_ONE);
        clock.advance(Duration.ofSeconds(10));
        service.playerDisconnected(fixture.matchId(), PLAYER_TWO);
        clock.advance(Duration.ofSeconds(55));

        service.playerConnected(fixture.matchId(), PLAYER_TWO);

        PlayerMatchView terminal = view(fixture);
        assertEquals(TerminalReason.DISCONNECT_FORFEIT, terminal.terminalResult().reason());
        assertEquals(PlayerSide.PLAYER_TWO, terminal.terminalResult().winner());
    }

    @Test
    void rankedCumulativeDisconnectAllowanceForfeitsExactlyAtTwoMinutes() {
        Fixture fixture = occupied(MatchMode.RANKED, TimerMode.STANDARD_15_PLUS_5);
        disconnectInterval(fixture, Duration.ofSeconds(50));
        disconnectInterval(fixture, Duration.ofSeconds(50));
        service.playerDisconnected(fixture.matchId(), PLAYER_ONE);
        clock.advance(Duration.ofSeconds(20));

        service.evaluateAllDeadlines();

        assertEquals(
                TerminalReason.CUMULATIVE_DISCONNECT_FORFEIT,
                view(fixture).terminalResult().reason());
    }

    @Test
    void restartFinalizesPersistedOverdueDeadlineIdempotently() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        MatchSnapshotCodec codec = new MatchSnapshotCodec(
                JsonMapper.builder().findAndAddModules().build());
        String persisted = codec.encode(repository.findById(fixture.matchId()).orElseThrow());
        clock.advance(MatchTimingRules.INITIAL_PLAY_TIME);
        InMemoryMatchRepository restoredRepository = new InMemoryMatchRepository();
        restoredRepository.save(codec.decode(persisted));
        PlayerMatchViewMapper restoredMapper = new PlayerMatchViewMapper();
        restoredMapper.setClock(clock);
        MatchApplicationService restarted = new MatchApplicationService(
                restoredRepository, restoredMapper);
        restarted.setClock(clock);

        restarted.recoverAfterRestart();
        PlayerMatchView terminal = restarted.getView(fixture.matchId(), PLAYER_ONE);
        long terminalVersion = terminal.version();
        restarted.recoverAfterRestart();

        assertEquals(TerminalReason.TIMEOUT, terminal.terminalResult().reason());
        assertEquals(terminalVersion, restarted.getView(fixture.matchId(), PLAYER_ONE).version());
    }

    @Test
    void periodicTimerSyncAdvancesLiveSequenceWithoutCreatingACommandVersion() {
        Fixture fixture = activeTimedMatch(MatchMode.CASUAL);
        PlayerMatchView before = view(fixture);

        service.publishTimerSyncs();

        PlayerMatchView synchronizedView = view(fixture);
        assertEquals(before.version(), synchronizedView.version());
        assertEquals(before.liveSequence() + 1, synchronizedView.liveSequence());
    }

    private void disconnectInterval(Fixture fixture, Duration duration) {
        service.playerDisconnected(fixture.matchId(), PLAYER_ONE);
        clock.advance(duration);
        assertDoesNotThrow(() -> service.playerConnected(fixture.matchId(), PLAYER_ONE));
    }

    private Fixture activeTimedMatch(MatchMode mode) {
        return active(occupied(mode, TimerMode.STANDARD_15_PLUS_5));
    }

    private Fixture activeUntimedMatch() {
        return active(occupied(MatchMode.CASUAL, TimerMode.CASUAL_UNTIMED));
    }

    private Fixture active(Fixture fixture) {
        long version = view(fixture).version();
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), PLAYER_ONE, version,
                formation(PlayerSide.PLAYER_ONE)));
        version = view(fixture).version();
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), PLAYER_TWO, version,
                formation(PlayerSide.PLAYER_TWO)));
        version = view(fixture).version();
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), PLAYER_ONE, version));
        version = view(fixture).version();
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), PLAYER_TWO, version));
        return fixture;
    }

    private Fixture occupied(MatchMode mode, TimerMode timerMode) {
        MatchCommandResult created = service.createMatch(
                new CreateMatchCommand(PLAYER_ONE, mode, timerMode));
        service.playerConnected(created.view().matchId(), PLAYER_ONE);
        service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), PLAYER_TWO, created.version()));
        service.playerConnected(created.view().matchId(), PLAYER_TWO);
        return new Fixture(created.view().matchId());
    }

    private PlayerMatchView view(Fixture fixture) {
        return service.getView(fixture.matchId(), PLAYER_ONE);
    }

    private MakeMoveCommand legalMove(Fixture fixture, PlayerMatchView view) {
        boolean playerOne = view.currentPlayer() == PlayerSide.PLAYER_ONE;
        return new MakeMoveCommand(
                UUID.randomUUID(),
                fixture.matchId(),
                player(view.currentPlayer()),
                view.version(),
                playerOne ? new Position(2, 0) : new Position(5, 0),
                playerOne ? new Position(3, 0) : new Position(4, 0));
    }

    private static String player(PlayerSide side) {
        return side == PlayerSide.PLAYER_ONE ? PLAYER_ONE : PLAYER_TWO;
    }

    private static List<FormationPiece> formation(PlayerSide side) {
        List<Rank> ranks = new ArrayList<>(List.of(
                Rank.FIVE_STAR_GENERAL,
                Rank.FOUR_STAR_GENERAL,
                Rank.THREE_STAR_GENERAL,
                Rank.TWO_STAR_GENERAL,
                Rank.ONE_STAR_GENERAL,
                Rank.COLONEL,
                Rank.LIEUTENANT_COLONEL,
                Rank.MAJOR,
                Rank.CAPTAIN,
                Rank.FIRST_LIEUTENANT,
                Rank.SECOND_LIEUTENANT,
                Rank.SERGEANT,
                Rank.SPY,
                Rank.SPY,
                Rank.FLAG));
        for (int count = 0; count < 6; count++) {
            ranks.add(Rank.PRIVATE);
        }
        int startRow = side == PlayerSide.PLAYER_ONE ? 0 : 5;
        List<FormationPiece> pieces = new ArrayList<>();
        for (int index = 0; index < ranks.size(); index++) {
            pieces.add(new FormationPiece(
                    UUID.randomUUID(),
                    ranks.get(index),
                    new Position(startRow + index / 9, index % 9)));
        }
        return pieces;
    }

    private record Fixture(UUID matchId) {
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
