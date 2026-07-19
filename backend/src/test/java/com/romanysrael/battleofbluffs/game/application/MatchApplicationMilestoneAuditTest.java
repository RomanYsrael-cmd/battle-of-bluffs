package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.*;
import static org.junit.jupiter.api.Assertions.*;

import com.romanysrael.battleofbluffs.game.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MatchApplicationMilestoneAuditTest {
    private InMemoryMatchRepository repository;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMatchRepository();
        service = new MatchApplicationService(repository, new PlayerMatchViewMapper());
    }

    @Test
    void matchCreationSeatsOnlyTheCreatorAndRoomCodeLookupFindsTheMatch() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("alice"));

        assertEquals(MatchApplicationService.INITIAL_VERSION, created.version());
        assertEquals(PlayerSide.PLAYER_ONE, created.view().requestingSide());
        assertTrue(created.view().playerOneOccupied());
        assertFalse(created.view().playerTwoOccupied());
        assertEquals(created.view().matchId(),
                service.getViewByRoomCode(created.view().roomCode().toLowerCase(Locale.ROOT), "alice").matchId());
    }

    @Test
    void secondPlayerJoinsButCreatorCannotTakeBothSeatsAndThirdPlayerIsRejected() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("alice"));
        UUID matchId = created.view().matchId();
        String roomCode = created.view().roomCode();

        assertCode(MatchErrorCode.INVALID_ROOM_STATE, () -> service.joinMatch(matchId,
                new JoinMatchCommand(UUID.randomUUID(), roomCode, "alice", 1)));
        MatchCommandResult joined = service.joinMatch(matchId,
                new JoinMatchCommand(UUID.randomUUID(), roomCode, "bob", 1));

        assertEquals(PlayerSide.PLAYER_TWO, joined.view().requestingSide());
        assertTrue(joined.view().playerOneOccupied());
        assertTrue(joined.view().playerTwoOccupied());
        assertCode(MatchErrorCode.MATCH_FULL, () -> service.joinMatch(matchId,
                new JoinMatchCommand(UUID.randomUUID(), roomCode, "charlie", 2)));
    }

    @Test
    void unknownRoomCodeUnknownMatchAndUnknownViewerAreRejected() {
        assertCode(MatchErrorCode.MATCH_NOT_FOUND, () -> service.joinMatch(
                UUID.randomUUID(), new JoinMatchCommand(UUID.randomUUID(), "UNKNOWN", "bob", 1)));
        assertCode(MatchErrorCode.MATCH_NOT_FOUND,
                () -> service.getView(UUID.randomUUID(), "alice"));

        Fixture fixture = fixture();
        assertCode(MatchErrorCode.PLAYER_NOT_IN_MATCH,
                () -> service.getView(fixture.matchId(), "mallory"));
    }

    @ParameterizedTest(name = "invalid formation: {0}")
    @MethodSource("invalidFormations")
    void completeInventoryAndPlacementRulesRejectEachInvalidFormationIndependently(
            String description, UnaryOperator<List<FormationPiece>> mutation) {
        Fixture fixture = fixture();
        List<FormationPiece> invalid = mutation.apply(formation(PlayerSide.PLAYER_ONE));

        assertCode(MatchErrorCode.INVALID_FORMATION, () -> service.submitFormation(
                new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(), "alice", 2, invalid)));
        assertEquals(2, service.getView(fixture.matchId(), "alice").version(), description);
    }

    static Stream<Arguments> invalidFormations() {
        return Stream.of(
                Arguments.of("missing piece", (UnaryOperator<List<FormationPiece>>) pieces ->
                        new ArrayList<>(pieces.subList(0, 20))),
                Arguments.of("extra piece", (UnaryOperator<List<FormationPiece>>) pieces -> {
                    List<FormationPiece> result = new ArrayList<>(pieces);
                    result.add(new FormationPiece(UUID.randomUUID(), Rank.PRIVATE, new Position(2, 8)));
                    return result;
                }),
                Arguments.of("incorrect rank quantity", (UnaryOperator<List<FormationPiece>>) pieces ->
                        replace(pieces, 0, item -> new FormationPiece(
                                item.pieceId(), Rank.PRIVATE, item.position()))),
                Arguments.of("duplicate piece ID", (UnaryOperator<List<FormationPiece>>) pieces ->
                        replace(pieces, 1, item -> new FormationPiece(
                                pieces.get(0).pieceId(), item.rank(), item.position()))),
                Arguments.of("duplicate position", (UnaryOperator<List<FormationPiece>>) pieces ->
                        replace(pieces, 1, item -> new FormationPiece(
                                item.pieceId(), item.rank(), pieces.get(0).position()))),
                Arguments.of("outside deployment zone", (UnaryOperator<List<FormationPiece>>) pieces ->
                        replace(pieces, 0, item -> new FormationPiece(
                                item.pieceId(), item.rank(), new Position(3, 0)))));
    }

    @Test
    void validFormationCanBeReplacedBeforeLockButNotModifiedAfterLock() {
        Fixture fixture = fixture();
        List<FormationPiece> first = formation(PlayerSide.PLAYER_ONE);
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 2, first));
        List<FormationPiece> replacement = new ArrayList<>(first);
        Collections.swap(replacement, 0, 1);

        MatchCommandResult replaced = service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 3, replacement));
        assertEquals(replacement.get(0).pieceId(), replaced.view().ownPieces().get(0).id());

        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 4));
        assertCode(MatchErrorCode.ALREADY_LOCKED, () -> service.submitFormation(
                new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(), "alice", 5, first)));
    }

    @Test
    void lockingRequiresAFormationAndOneLockDoesNotStartTheMatch() {
        Fixture fixture = fixture();
        assertCode(MatchErrorCode.INVALID_FORMATION, () -> service.lockFormation(
                new LockFormationCommand(UUID.randomUUID(), fixture.matchId(), "alice", 2)));

        service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(),
                "alice", 2, formation(PlayerSide.PLAYER_ONE)));
        MatchCommandResult locked = service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 3));

        assertEquals(MatchPhase.FORMATION, locked.view().phase());
        assertTrue(locked.view().playerOneLocked());
        assertFalse(locked.view().playerTwoLocked());
    }

    @Test
    void bothValidLocksStartTheMatchAndStartedEventIdentifiesTheSelectedFirstPlayer() {
        Fixture fixture = fixture();
        long version = submitBoth(fixture, 2);
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", version++));
        PlayerMatchView started = service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "bob", version)).view();

        assertEquals(MatchPhase.ACTIVE, started.phase());
        assertNotNull(started.currentPlayer());
        assertTrue(EnumSet.of(PlayerSide.PLAYER_ONE, PlayerSide.PLAYER_TWO)
                .contains(started.currentPlayer()));
        PublicMatchEvent.Type startType = PublicMatchEvent.Type.MATCH_STARTED;
        assertEquals(started.currentPlayer(), started.events().stream()
                .filter(event -> event.type() == startType)
                .findFirst().orElseThrow().actor());
    }

    @Test
    void outOfTurnAndStaleMovesAreRejectedWithoutChangingVersionOrBoard() {
        Fixture fixture = activeFixture();
        PlayerMatchView before = service.getView(fixture.matchId(), "alice");
        String mover = playerId(before.currentPlayer());
        String other = mover.equals("alice") ? "bob" : "alice";
        Position source = frontSource(before.currentPlayer(), 0);
        Position destination = forward(source, before.currentPlayer());

        assertCode(MatchErrorCode.ILLEGAL_MOVE, () -> service.makeMove(new MakeMoveCommand(
                UUID.randomUUID(), fixture.matchId(), other, before.version(), source, destination)));
        assertCode(MatchErrorCode.STALE_VERSION, () -> service.makeMove(new MakeMoveCommand(
                UUID.randomUUID(), fixture.matchId(), mover, before.version() - 1, source, destination)));

        PlayerMatchView after = service.getView(fixture.matchId(), mover);
        assertEquals(before.version(), after.version());
        assertTrue(after.ownPieces().stream().anyMatch(piece -> source.equals(piece.position())));
    }

    @Test
    void legalMoveAppliesOnceAndIncrementsVersionExactlyOnce() {
        Fixture fixture = activeFixture();
        PlayerMatchView before = service.getView(fixture.matchId(), "alice");
        String mover = playerId(before.currentPlayer());
        Position source = frontSource(before.currentPlayer(), 0);
        Position destination = forward(source, before.currentPlayer());

        MatchCommandResult result = service.makeMove(new MakeMoveCommand(
                UUID.randomUUID(), fixture.matchId(), mover, before.version(), source, destination));

        assertEquals(before.version() + 1, result.version());
        assertTrue(result.view().ownPieces().stream()
                .anyMatch(piece -> destination.equals(piece.position())));
        assertFalse(result.view().ownPieces().stream()
                .anyMatch(piece -> source.equals(piece.position())));
    }

    @Test
    void exactRetryReturnsStoredResultWithoutApplyingStateTwiceAndChangedPayloadConflicts() {
        Fixture fixture = activeFixture();
        PlayerMatchView before = service.getView(fixture.matchId(), "alice");
        String mover = playerId(before.currentPlayer());
        Position source = frontSource(before.currentPlayer(), 0);
        Position destination = forward(source, before.currentPlayer());
        UUID commandId = UUID.randomUUID();
        MakeMoveCommand command = new MakeMoveCommand(
                commandId, fixture.matchId(), mover, before.version(), source, destination);

        MatchCommandResult first = service.makeMove(command);
        MatchCommandResult retry = service.makeMove(command);

        assertSame(first, retry);
        assertEquals(first.version(), service.getView(fixture.matchId(), mover).version());
        assertCode(MatchErrorCode.COMMAND_CONFLICT, () -> service.makeMove(new MakeMoveCommand(
                commandId, fixture.matchId(), mover, before.version(), source,
                new Position(destination.row(), 1))));
    }

    @Test
    void logicallyIdenticalFormationRetryIgnoresListOrdering() {
        Fixture fixture = fixture();
        UUID commandId = UUID.randomUUID();
        List<FormationPiece> pieces = formation(PlayerSide.PLAYER_ONE);
        SubmitFormationCommand original = new SubmitFormationCommand(
                commandId, fixture.matchId(), "alice", 2, pieces);
        MatchCommandResult first = service.submitFormation(original);
        List<FormationPiece> reordered = new ArrayList<>(pieces);
        Collections.reverse(reordered);

        MatchCommandResult retry = service.submitFormation(new SubmitFormationCommand(
                commandId, fixture.matchId(), "alice", 2, reordered));

        assertSame(first, retry);
        assertEquals(3, service.getView(fixture.matchId(), "alice").version());
    }

    @Test
    void changedFormationWithSameCommandIdIsRejected() {
        Fixture fixture = fixture();
        UUID commandId = UUID.randomUUID();
        List<FormationPiece> pieces = formation(PlayerSide.PLAYER_ONE);
        service.submitFormation(new SubmitFormationCommand(
                commandId, fixture.matchId(), "alice", 2, pieces));
        List<FormationPiece> changed = replace(pieces, 0, item -> new FormationPiece(
                item.pieceId(), item.rank(), new Position(2, 8)));

        assertCode(MatchErrorCode.COMMAND_CONFLICT, () -> service.submitFormation(
                new SubmitFormationCommand(commandId, fixture.matchId(), "alice", 2, changed)));
    }

    @Test
    void commandHistoryIsBoundedToTheDocumentedInMemoryLimit() {
        Fixture fixture = fixture();
        for (int index = 0; index < 257; index++) {
            service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(),
                    "alice", 2L + index, formation(PlayerSide.PLAYER_ONE)));
        }

        PrivateMatch match = repository.findById(fixture.matchId()).orElseThrow();
        assertEquals(256, match.commands.size());
    }

    @Test
    void twoSimultaneousCommandsAgainstOneVersionYieldOneSuccessAndOneStaleConflict()
            throws Exception {
        Fixture fixture = activeFixture();
        PlayerMatchView before = service.getView(fixture.matchId(), "alice");
        String mover = playerId(before.currentPlayer());
        Position source = frontSource(before.currentPlayer(), 0);
        Position destination = forward(source, before.currentPlayer());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> simultaneousMove(
                            fixture, mover, before.version(), source, destination, ready, start)),
                    executor.submit(() -> simultaneousMove(
                            fixture, mover, before.version(), source, destination, ready, start)));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(5, TimeUnit.SECONDS));
            }

            assertEquals(1, outcomes.stream().filter(MatchCommandResult.class::isInstance).count());
            assertEquals(List.of(MatchErrorCode.STALE_VERSION), outcomes.stream()
                    .filter(MatchErrorCode.class::isInstance)
                    .map(MatchErrorCode.class::cast)
                    .toList());
            PlayerMatchView after = service.getView(fixture.matchId(), mover);
            assertEquals(before.version() + 1, after.version());
            assertTrue(after.ownPieces().stream()
                    .anyMatch(piece -> destination.equals(piece.position())));
            assertFalse(after.ownPieces().stream()
                    .anyMatch(piece -> source.equals(piece.position())));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void submittedPieceIdsAreReplacedByStableOpaqueIdsInOpponentViews() {
        Fixture fixture = fixture();
        List<FormationPiece> alice = formation(PlayerSide.PLAYER_ONE);
        List<String> maliciousLabels = List.of("FLAG", "SPY-LEFT", "FIVE_STAR_GENERAL");
        for (int index = 0; index < maliciousLabels.size(); index++) {
            FormationPiece original = alice.get(index);
            alice.set(index, new FormationPiece(
                    namedId(maliciousLabels.get(index)), original.rank(), original.position()));
        }
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 2, alice));
        service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(),
                "bob", 3, formation(PlayerSide.PLAYER_TWO)));

        PlayerMatchView first = service.getView(fixture.matchId(), "bob");
        PlayerMatchView second = service.getView(fixture.matchId(), "bob");
        Set<UUID> submittedIds = alice.stream().map(FormationPiece::pieceId)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(first.opponentPieces(), second.opponentPieces());
        assertTrue(first.opponentPieces().stream()
                .map(PlayerMatchView.OpponentPieceView::id)
                .noneMatch(submittedIds::contains));
        String opponentPayload = first.opponentPieces().toString();
        for (String label : maliciousLabels) {
            assertFalse(opponentPayload.contains(label));
            assertFalse(opponentPayload.contains(namedId(label).toString()));
        }
    }

    @Test
    void activeViewsHideAllOpponentRanksAndTerminalViewsRevealCompleteFormations() {
        Fixture fixture = activeFixture();
        PlayerMatchView aliceActive = service.getView(fixture.matchId(), "alice");
        PlayerMatchView bobActive = service.getView(fixture.matchId(), "bob");

        assertEquals(21, aliceActive.opponentPieces().size());
        assertEquals(21, bobActive.opponentPieces().size());
        assertTrue(aliceActive.postMatchPieces().isEmpty());
        assertTrue(bobActive.postMatchPieces().isEmpty());
        assertTrue(Arrays.stream(PlayerMatchView.OpponentPieceView.class.getRecordComponents())
                .noneMatch(component -> component.getName().equals("rank")));

        String resigner = playerId(aliceActive.currentPlayer());
        service.resign(new ResignCommand(UUID.randomUUID(), fixture.matchId(),
                resigner, aliceActive.version()));
        PlayerMatchView aliceTerminal = service.getView(fixture.matchId(), "alice");
        PlayerMatchView bobTerminal = service.getView(fixture.matchId(), "bob");

        assertEquals(42, aliceTerminal.postMatchPieces().size());
        assertEquals(42, bobTerminal.postMatchPieces().size());
        assertTrue(aliceTerminal.postMatchPieces().stream().allMatch(piece -> piece.rank() != null));
        assertTrue(bobTerminal.postMatchPieces().stream().allMatch(piece -> piece.rank() != null));
    }

    @Test
    void battleUsesDomainResolutionHidesRanksAndMapsRemovedOpponentIdToOpaqueId() {
        Fixture fixture = fixtureWithFormations(
                placeRankAt(formation(PlayerSide.PLAYER_ONE), Rank.FIVE_STAR_GENERAL,
                        new Position(2, 0)),
                placeRankAt(formation(PlayerSide.PLAYER_TWO), Rank.FLAG,
                        new Position(5, 0)));
        PlayerMatchView view = service.getView(fixture.matchId(), "alice");
        long version = view.version();
        boolean playerTwoStarted = view.currentPlayer() == PlayerSide.PLAYER_TWO;
        int bobRow = 5;
        if (playerTwoStarted) {
            version = move(fixture, "bob", version, new Position(5, 8), new Position(4, 8));
            bobRow = 4;
        }
        version = move(fixture, "alice", version, new Position(2, 0), new Position(3, 0));
        version = move(fixture, "bob", version,
                new Position(bobRow, 8), new Position(bobRow - 1, 8));
        bobRow--;
        version = move(fixture, "alice", version, new Position(3, 0), new Position(4, 0));
        if (playerTwoStarted) {
            version = move(fixture, "bob", version,
                    new Position(bobRow, 8), new Position(bobRow - 1, 8));
        }
        service.makeMove(new MakeMoveCommand(UUID.randomUUID(), fixture.matchId(), "alice",
                version, new Position(4, 0), new Position(5, 0)));

        PlayerMatchView alice = service.getView(fixture.matchId(), "alice");
        PlayerMatchView bob = service.getView(fixture.matchId(), "bob");
        PlayerMatchView.EventView aliceBattle = battleEvent(alice);
        PlayerMatchView.EventView bobBattle = battleEvent(bob);
        UUID submittedFlagId = formationPieceAt(
                repository.findById(fixture.matchId()).orElseThrow().formations.get(PlayerSide.PLAYER_TWO),
                new Position(5, 0)).id();

        assertEquals(MatchPhase.TERMINAL, alice.phase());
        assertEquals(TerminalReason.FLAG_CAPTURE, alice.terminalResult().reason());
        assertEquals(PlayerSide.PLAYER_ONE, alice.terminalResult().winner());
        assertEquals("OWN_PIECE_WON", aliceBattle.ownBattleOutcome());
        assertEquals("OWN_PIECE_LOST", bobBattle.ownBattleOutcome());
        assertNotEquals(submittedFlagId, aliceBattle.removedPieceIds().get(0));
        assertEquals(submittedFlagId, bobBattle.removedPieceIds().get(0));
        assertFalse(aliceBattle.toString().contains("FLAG"));
    }

    @Test
    void resignationMakesTerminalResultImmutableAndNewCommandsAreRejected() {
        Fixture fixture = activeFixture();
        PlayerMatchView before = service.getView(fixture.matchId(), "alice");
        MatchCommandResult resigned = service.resign(new ResignCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", before.version()));
        TerminalResult terminalResult = resigned.view().terminalResult();

        assertEquals(MatchPhase.TERMINAL, resigned.view().phase());
        assertEquals(PlayerSide.PLAYER_TWO, terminalResult.winner());
        assertEquals(TerminalReason.RESIGNATION, terminalResult.reason());
        assertCode(MatchErrorCode.TERMINAL_MATCH, () -> service.resign(new ResignCommand(
                UUID.randomUUID(), fixture.matchId(), "bob", resigned.version())));
        assertCode(MatchErrorCode.TERMINAL_MATCH, () -> service.makeMove(new MakeMoveCommand(
                UUID.randomUUID(), fixture.matchId(), "bob", resigned.version(),
                new Position(5, 0), new Position(4, 0))));
        assertEquals(terminalResult, service.getView(fixture.matchId(), "bob").terminalResult());
        assertEquals(resigned.version(), service.getView(fixture.matchId(), "bob").version());
    }

    private Object simultaneousMove(
            Fixture fixture, String playerId, long version, Position source, Position destination,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            return service.makeMove(new MakeMoveCommand(UUID.randomUUID(), fixture.matchId(),
                    playerId, version, source, destination));
        } catch (MatchApplicationException exception) {
            return exception.code();
        }
    }

    private Fixture fixture() {
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("alice"));
        service.joinMatch(created.view().matchId(), new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), "bob", 1));
        return new Fixture(created.view().matchId());
    }

    private Fixture activeFixture() {
        Fixture fixture = fixture();
        long version = submitBoth(fixture, 2);
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", version++));
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "bob", version));
        return fixture;
    }

    private Fixture fixtureWithFormations(List<FormationPiece> playerOne, List<FormationPiece> playerTwo) {
        Fixture fixture = fixture();
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 2, playerOne));
        service.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "bob", 3, playerTwo));
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "alice", 4));
        service.lockFormation(new LockFormationCommand(
                UUID.randomUUID(), fixture.matchId(), "bob", 5));
        return fixture;
    }

    private long submitBoth(Fixture fixture, long version) {
        service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(),
                "alice", version++, formation(PlayerSide.PLAYER_ONE)));
        service.submitFormation(new SubmitFormationCommand(UUID.randomUUID(), fixture.matchId(),
                "bob", version++, formation(PlayerSide.PLAYER_TWO)));
        return version;
    }

    private long move(Fixture fixture, String playerId, long version, Position source, Position destination) {
        return service.makeMove(new MakeMoveCommand(UUID.randomUUID(), fixture.matchId(), playerId,
                version, source, destination)).version();
    }

    private static List<FormationPiece> formation(PlayerSide side) {
        List<Rank> ranks = new ArrayList<>(List.of(
                Rank.FIVE_STAR_GENERAL, Rank.FOUR_STAR_GENERAL, Rank.THREE_STAR_GENERAL,
                Rank.TWO_STAR_GENERAL, Rank.ONE_STAR_GENERAL, Rank.COLONEL,
                Rank.LIEUTENANT_COLONEL, Rank.MAJOR, Rank.CAPTAIN, Rank.FIRST_LIEUTENANT,
                Rank.SECOND_LIEUTENANT, Rank.SERGEANT, Rank.SPY, Rank.SPY, Rank.FLAG));
        for (int index = 0; index < 6; index++) {
            ranks.add(Rank.PRIVATE);
        }
        int startRow = side == PlayerSide.PLAYER_ONE ? 0 : 5;
        List<FormationPiece> pieces = new ArrayList<>();
        for (int index = 0; index < ranks.size(); index++) {
            pieces.add(new FormationPiece(UUID.randomUUID(), ranks.get(index),
                    new Position(startRow + index / 9, index % 9)));
        }
        return pieces;
    }

    private static List<FormationPiece> placeRankAt(
            List<FormationPiece> pieces, Rank rank, Position destination) {
        List<FormationPiece> result = new ArrayList<>(pieces);
        int rankIndex = indexOf(result, piece -> piece.rank() == rank);
        int destinationIndex = indexOf(result, piece -> piece.position().equals(destination));
        FormationPiece rankedPiece = result.get(rankIndex);
        FormationPiece displacedPiece = result.get(destinationIndex);
        result.set(rankIndex, new FormationPiece(
                rankedPiece.pieceId(), rankedPiece.rank(), displacedPiece.position()));
        result.set(destinationIndex, new FormationPiece(
                displacedPiece.pieceId(), displacedPiece.rank(), rankedPiece.position()));
        return result;
    }

    private static int indexOf(
            List<FormationPiece> pieces, java.util.function.Predicate<FormationPiece> predicate) {
        for (int index = 0; index < pieces.size(); index++) {
            if (predicate.test(pieces.get(index))) {
                return index;
            }
        }
        throw new AssertionError("Formation piece not found");
    }

    private static List<FormationPiece> replace(
            List<FormationPiece> pieces, int index, UnaryOperator<FormationPiece> mutation) {
        List<FormationPiece> result = new ArrayList<>(pieces);
        result.set(index, mutation.apply(result.get(index)));
        return result;
    }

    private static String playerId(PlayerSide side) {
        return side == PlayerSide.PLAYER_ONE ? "alice" : "bob";
    }

    private static Position frontSource(PlayerSide side, int column) {
        return new Position(side == PlayerSide.PLAYER_ONE ? 2 : 5, column);
    }

    private static Position forward(Position source, PlayerSide side) {
        return new Position(source.row() + (side == PlayerSide.PLAYER_ONE ? 1 : -1), source.column());
    }

    private static UUID namedId(String label) {
        return UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8));
    }

    private static Piece formationPieceAt(List<Piece> pieces, Position position) {
        return pieces.stream().filter(piece -> piece.position().orElseThrow().equals(position))
                .findFirst().orElseThrow();
    }

    private static PlayerMatchView.EventView battleEvent(PlayerMatchView view) {
        return view.events().stream()
                .filter(event -> event.type() == PublicMatchEvent.Type.BATTLE_RESOLVED)
                .findFirst().orElseThrow();
    }

    private void assertCode(MatchErrorCode expected, Runnable action) {
        MatchApplicationException exception = assertThrows(MatchApplicationException.class, action::run);
        assertEquals(expected, exception.code());
    }

    private record Fixture(UUID matchId) { }
}
