package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.CancelMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.FormationPiece;
import static com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.LeaveMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.LockFormationCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.ResignCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.SubmitFormationCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchLifecycleServiceTest {
    private InMemoryMatchRepository repository;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMatchRepository();
        service = new MatchApplicationService(repository, new PlayerMatchViewMapper());
    }

    @Test
    void persistedWaitingRoomIsDiscoveredAfterAServiceRestartWithoutBrowserStorage() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));

        MatchApplicationService restarted = new MatchApplicationService(
                repository, new PlayerMatchViewMapper());
        List<MatchApplicationService.CurrentMatchSummary> current =
                restarted.currentMatches("host");

        assertEquals(1, current.size());
        MatchApplicationService.CurrentMatchSummary summary = current.get(0);
        assertEquals(created.view().matchId(), summary.matchId());
        assertEquals(created.view().roomCode(), summary.roomCode());
        assertEquals(PlayerSide.PLAYER_ONE, summary.side());
        assertFalse(summary.opponentPresent());
        assertTrue(summary.canResume());
        assertTrue(summary.canCancel());
        assertFalse(summary.canLeave());
        assertEquals("/matches/" + created.view().matchId(), summary.resumeRoute());
    }

    @Test
    void hostCancelsWaitingRoomIdempotentlyAndChangedRetryPayloadConflicts() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        UUID commandId = UUID.randomUUID();
        CancelMatchCommand command = new CancelMatchCommand(
                commandId, created.view().matchId(), "host", created.version());

        MatchLifecycleResult cancelled = service.cancelMatch(command);
        MatchLifecycleResult retry = service.cancelMatch(command);

        assertSame(cancelled, retry);
        assertEquals(created.version() + 1, cancelled.version());
        assertEquals(MatchLifecycleResult.Action.ROOM_CANCELLED, cancelled.action());
        assertTrue(service.currentMatches("host").isEmpty());
        PlayerMatchView terminal = service.getView(created.view().matchId(), "host");
        assertEquals(MatchPhase.TERMINAL, terminal.phase());
        assertEquals(TerminalReason.ROOM_CANCELLED, terminal.terminalResult().reason());
        assertTrue(terminal.postMatchPieces().isEmpty());
        assertCode(MatchErrorCode.COMMAND_CONFLICT, () -> service.cancelMatch(
                new CancelMatchCommand(
                        commandId, created.view().matchId(), "host", created.version() + 1)));
    }

    @Test
    void unlockedGuestLeavesAndSeatFormationAndEligibilityAreClearedForReplacement() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        MatchCommandResult joined = service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "guest", created.version()));
        MatchCommandResult formed = service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), created.view().matchId(), "guest", joined.version(),
                formation(PlayerSide.PLAYER_TWO)));

        MatchLifecycleResult left = service.leaveMatch(new LeaveMatchCommand(
                UUID.randomUUID(), created.view().matchId(), "guest", formed.version()));

        assertEquals(MatchLifecycleResult.Action.LOBBY_LEFT, left.action());
        assertTrue(service.currentMatches("guest").isEmpty());
        MatchApplicationService.CurrentMatchSummary host =
                service.currentMatches("host").get(0);
        assertFalse(host.opponentPresent());
        assertTrue(host.canCancel());
        PlayerMatchView hostView = service.getView(created.view().matchId(), "host");
        assertFalse(hostView.playerTwoOccupied());
        assertTrue(hostView.opponentPieces().isEmpty());

        MatchCommandResult replacement = service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "replacement", left.version()));
        assertEquals(PlayerSide.PLAYER_TWO, replacement.view().requestingSide());
    }

    @Test
    void hostCancellationWithGuestRemovesBothPlayersFromOpenMatchEligibility() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        MatchCommandResult joined = service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "guest", created.version()));

        service.cancelMatch(new CancelMatchCommand(
                UUID.randomUUID(), created.view().matchId(), "host", joined.version()));

        assertFalse(service.hasActiveMatch("host"));
        assertFalse(service.hasActiveMatch("guest"));
    }

    @Test
    void activeMatchRejectsLobbyActionsButStillAcceptsResignation() {
        ActiveFixture active = activeCasual();

        assertCode(MatchErrorCode.INVALID_ROOM_STATE, () -> service.cancelMatch(
                new CancelMatchCommand(UUID.randomUUID(), active.matchId(), "host", active.version())));
        assertCode(MatchErrorCode.INVALID_ROOM_STATE, () -> service.leaveMatch(
                new LeaveMatchCommand(UUID.randomUUID(), active.matchId(), "guest", active.version())));

        MatchCommandResult resigned = service.resign(new ResignCommand(
                UUID.randomUUID(), active.matchId(), "guest", active.version()));
        assertEquals(TerminalReason.RESIGNATION, resigned.view().terminalResult().reason());
    }

    @Test
    void rankedPairingRejectsCasualCancelAndLeaveActions() {
        MatchApplicationService.RankedMatch ranked = service.createRankedMatch("host", "guest");
        long version = ranked.playerOneView().version();

        assertCode(MatchErrorCode.INVALID_ROOM_STATE, () -> service.cancelMatch(
                new CancelMatchCommand(UUID.randomUUID(), ranked.matchId(), "host", version)));
        assertCode(MatchErrorCode.INVALID_ROOM_STATE, () -> service.leaveMatch(
                new LeaveMatchCommand(UUID.randomUUID(), ranked.matchId(), "guest", version)));
    }

    @Test
    void outsiderCannotViewCancelOrLeaveAnotherPlayersRoom() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));

        assertCode(MatchErrorCode.PLAYER_NOT_IN_MATCH, () -> service.getView(
                created.view().matchId(), "outsider"));
        assertCode(MatchErrorCode.PLAYER_NOT_IN_MATCH, () -> service.cancelMatch(
                new CancelMatchCommand(
                        UUID.randomUUID(), created.view().matchId(), "outsider", created.version())));
        assertCode(MatchErrorCode.PLAYER_NOT_IN_MATCH, () -> service.leaveMatch(
                new LeaveMatchCommand(
                        UUID.randomUUID(), created.view().matchId(), "outsider", created.version())));
    }

    @Test
    void summariesExposeNoPieceOrOpponentIdentityDataAndTerminalRoomsDisappear() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "guest", created.version()));

        String summary = service.currentMatches("host").get(0).toString();
        assertFalse(summary.contains("Rank"));
        assertFalse(summary.contains("guest"));
        assertFalse(summary.contains("piece"));

        service.cancelMatch(new CancelMatchCommand(
                UUID.randomUUID(), created.view().matchId(), "host", 2));
        assertTrue(service.currentMatches("host").isEmpty());
    }

    @Test
    void lifecycleUpdatesPublishOnlyAfterPersistenceToTheRemainingParticipants() {
        List<MatchUpdatePublisher.MatchUpdate> updates = new ArrayList<>();
        service.setUpdatePublisher(updates::add);
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        MatchCommandResult joined = service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "guest", created.version()));
        updates.clear();

        service.leaveMatch(new LeaveMatchCommand(
                UUID.randomUUID(), created.view().matchId(), "guest", joined.version()));

        assertEquals(1, updates.size());
        assertEquals(MatchUpdatePublisher.UpdateType.PLAYER_LEFT, updates.get(0).type());
        assertEquals(Set.of("host"), updates.get(0).playerViews().keySet());
        assertFalse(updates.get(0).playerViews().get("host").playerTwoOccupied());
    }

    @Test
    void futureDuplicateParticipationIsRejectedWhileLegacyMultipleRoomsAreReturnedExplicitly() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        assertCode(MatchErrorCode.OPEN_MATCH_EXISTS, () -> service.createMatch(
                new CreateMatchCommand("host")));

        PrivateMatch legacy = new PrivateMatch(
                UUID.randomUUID(), "LEGACY", "host", MatchMode.CASUAL, TimerMode.CASUAL_UNTIMED);
        legacy.createdAt = Instant.EPOCH;
        legacy.updatedAt = Instant.EPOCH;
        repository.save(legacy);

        List<MatchApplicationService.CurrentMatchSummary> current = service.currentMatches("host");
        assertEquals(2, current.size());
        assertTrue(current.stream().anyMatch(summary -> summary.matchId().equals(created.view().matchId())));
        assertTrue(current.stream().anyMatch(summary -> summary.matchId().equals(legacy.id)));
    }

    @Test
    void simultaneousCancelAndLeaveAtTheSameVersionApplyExactlyOneLifecycleTransition()
            throws Exception {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        MatchCommandResult joined = service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "guest", created.version()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> cancel = executor.submit(() -> concurrentLifecycle(
                    ready,
                    start,
                    () -> service.cancelMatch(new CancelMatchCommand(
                            UUID.randomUUID(),
                            created.view().matchId(),
                            "host",
                            joined.version()))));
            Future<Object> leave = executor.submit(() -> concurrentLifecycle(
                    ready,
                    start,
                    () -> service.leaveMatch(new LeaveMatchCommand(
                            UUID.randomUUID(),
                            created.view().matchId(),
                            "guest",
                            joined.version()))));
            ready.await();
            start.countDown();

            List<Object> outcomes = List.of(cancel.get(), leave.get());
            assertEquals(1, outcomes.stream()
                    .filter(MatchLifecycleResult.class::isInstance)
                    .count());
            assertEquals(1, outcomes.stream()
                    .filter(MatchApplicationException.class::isInstance)
                    .count());
            MatchApplicationException rejected = (MatchApplicationException) outcomes.stream()
                    .filter(MatchApplicationException.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            assertEquals(MatchErrorCode.STALE_VERSION, rejected.code());
            assertEquals(
                    joined.version() + 1,
                    service.getView(created.view().matchId(), "host").version());
        } finally {
            executor.shutdownNow();
        }
    }

    private Object concurrentLifecycle(
            CountDownLatch ready,
            CountDownLatch start,
            java.util.concurrent.Callable<MatchLifecycleResult> command) throws Exception {
        ready.countDown();
        start.await();
        try {
            return command.call();
        } catch (MatchApplicationException exception) {
            return exception;
        }
    }

    private ActiveFixture activeCasual() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("host"));
        long version = service.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "guest", created.version())).version();
        version = service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), created.view().matchId(), "host", version,
                formation(PlayerSide.PLAYER_ONE))).version();
        version = service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), created.view().matchId(), "guest", version,
                formation(PlayerSide.PLAYER_TWO))).version();
        version = service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), created.view().matchId(), "host", version)).version();
        version = service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), created.view().matchId(), "guest", version)).version();
        return new ActiveFixture(created.view().matchId(), version);
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
        for (int index = 0; index < 6; index++) {
            ranks.add(Rank.PRIVATE);
        }
        int firstRow = side == PlayerSide.PLAYER_ONE ? 0 : 5;
        List<FormationPiece> pieces = new ArrayList<>();
        for (int index = 0; index < ranks.size(); index++) {
            pieces.add(new FormationPiece(
                    UUID.randomUUID(),
                    ranks.get(index),
                    new Position(firstRow + index / 9, index % 9)));
        }
        return pieces;
    }

    private static void assertCode(MatchErrorCode expected, Runnable command) {
        MatchApplicationException exception = assertThrows(
                MatchApplicationException.class, command::run);
        assertEquals(expected, exception.code());
    }

    private record ActiveFixture(UUID matchId, long version) {
    }
}
