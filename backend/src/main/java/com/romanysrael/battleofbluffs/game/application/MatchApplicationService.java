package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.*;

import com.romanysrael.battleofbluffs.game.domain.*;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class MatchApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchApplicationService.class);
    public static final long INITIAL_VERSION = 1;
    private static final int COMMAND_HISTORY_LIMIT = 256;
    private static final char[] ROOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Map<Rank, Integer> INVENTORY = Map.ofEntries(
            Map.entry(Rank.FIVE_STAR_GENERAL, 1), Map.entry(Rank.FOUR_STAR_GENERAL, 1),
            Map.entry(Rank.THREE_STAR_GENERAL, 1), Map.entry(Rank.TWO_STAR_GENERAL, 1),
            Map.entry(Rank.ONE_STAR_GENERAL, 1), Map.entry(Rank.COLONEL, 1),
            Map.entry(Rank.LIEUTENANT_COLONEL, 1), Map.entry(Rank.MAJOR, 1),
            Map.entry(Rank.CAPTAIN, 1), Map.entry(Rank.FIRST_LIEUTENANT, 1),
            Map.entry(Rank.SECOND_LIEUTENANT, 1), Map.entry(Rank.SERGEANT, 1),
            Map.entry(Rank.PRIVATE, 6), Map.entry(Rank.SPY, 2), Map.entry(Rank.FLAG, 1));
    private final MatchRepository repository;
    private final PlayerMatchViewMapper mapper;
    private final MatchTemporalService temporal;
    private MatchUpdatePublisher updatePublisher = update -> { };
    private MatchJoinPolicy joinPolicy = MatchJoinPolicy.ALLOW_ALL;
    private final MoveValidator moveValidator = new MoveValidator();
    private final BattleResolver battleResolver = new BattleResolver();
    private final VictoryEvaluator victoryEvaluator = new VictoryEvaluator();
    private final SecureRandom random = new SecureRandom();
    private final Object playerActivityLock = new Object();
    private Clock clock = Clock.systemUTC();
    private MatchTimingSettings timing = MatchTimingSettings.defaults();

    public MatchApplicationService(MatchRepository repository, PlayerMatchViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.temporal = new MatchTemporalService(repository, mapper);
    }

    @Autowired(required = false)
    void setUpdatePublisher(MatchUpdatePublisher updatePublisher) {
        this.updatePublisher = updatePublisher;
        this.temporal.setUpdatePublisher(updatePublisher);
    }

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
        this.temporal.setClock(clock);
    }

    @Autowired(required = false)
    void setJoinPolicy(MatchJoinPolicy joinPolicy) {
        this.joinPolicy = joinPolicy;
    }

    @Autowired(required = false)
    void setTiming(MatchTimingSettings timing) {
        this.timing = timing;
        this.temporal.setTiming(timing);
    }

    public MatchCommandResult createMatch(CreateMatchCommand command) {
        String playerId = blank(command.playerId()) ? opaquePlayerId() : command.playerId();
        synchronized (playerActivityLock) {
            requireNoOpenMatch(playerId);
            MatchMode mode = command.mode() == null ? MatchMode.CASUAL : command.mode();
            TimerMode timerMode = command.timerMode() == null
                    ? TimerMode.CASUAL_UNTIMED
                    : command.timerMode();
            if (mode == MatchMode.RANKED) {
                timerMode = TimerMode.STANDARD_15_PLUS_5;
            }
            UUID id = UUID.randomUUID();
            String code;
            do {
                code = roomCode();
            } while (repository.findByRoomCode(code).isPresent());
            PrivateMatch match = new PrivateMatch(id, code, playerId, mode, timerMode);
            Instant now = clock.instant();
            match.createdAt = now;
            match.updatedAt = now;
            addEvent(match, PublicMatchEvent.Type.MATCH_CREATED, PlayerSide.PLAYER_ONE,
                    null, null, null, null, null, List.of(), null);
            repository.save(match);
            return new MatchCommandResult(null, match.version, mapper.map(match, playerId));
        }
    }

    public RankedMatch createRankedMatch(String playerOneId, String playerTwoId) {
        if (blank(playerOneId) || blank(playerTwoId) || playerOneId.equals(playerTwoId)) {
            throw new IllegalArgumentException("Ranked matchmaking requires two distinct players");
        }
        synchronized (playerActivityLock) {
            requireNoOpenMatch(playerOneId);
            requireNoOpenMatch(playerTwoId);
            Instant createdAt = clock.instant();
            PrivateMatch match = new PrivateMatch(
                    UUID.randomUUID(),
                    null,
                    playerOneId,
                    MatchMode.RANKED,
                    TimerMode.STANDARD_15_PLUS_5);
            match.createdAt = createdAt;
            match.updatedAt = createdAt;
            match.players.put(PlayerSide.PLAYER_TWO, playerTwoId);
            match.participantCycleStartedAt = createdAt;
            temporal.openFormation(match, createdAt);
            addEvent(match, PublicMatchEvent.Type.MATCH_CREATED, PlayerSide.PLAYER_ONE,
                    null, null, null, null, null, List.of(), null);
            addEvent(match, PublicMatchEvent.Type.PLAYER_JOINED, PlayerSide.PLAYER_TWO,
                    null, null, null, null, null, List.of(), null);
            repository.save(match);
            return new RankedMatch(
                    match.id,
                    mapper.map(match, playerOneId),
                    mapper.map(match, playerTwoId));
        }
    }

    public MatchCommandResult joinMatch(JoinMatchCommand command) {
        PrivateMatch match = byCode(command.roomCode());
        synchronized (playerActivityLock) {
            synchronized (match) {
                Instant receivedAt = clock.instant();
                PrivateMatch.CommandFingerprint fingerprint = fingerprint(
                        "join",
                        canonicalRoomCode(command.roomCode()),
                        nullable(command.playerId()),
                        Long.toString(command.expectedVersion()));
                MatchCommandResult replay = replay(match, command.commandId(), fingerprint);
                if (replay != null) {
                    return replay;
                }
                temporal.ensureNotExpired(match, receivedAt);
                if (command.expectedVersion() > 0) {
                    version(match, command.expectedVersion());
                }
                String playerId = blank(command.playerId())
                        ? opaquePlayerId()
                        : command.playerId();
                if (match.sideOf(playerId) != null) {
                    throw error(
                            MatchErrorCode.INVALID_ROOM_STATE,
                            "Player already occupies a seat");
                }
                if (match.players.size() >= 2) {
                    throw error(MatchErrorCode.MATCH_FULL, "Match already has two players");
                }
                requireNoOpenMatch(playerId);
                if (!joinPolicy.mayJoin(
                        match.players.get(PlayerSide.PLAYER_ONE), playerId)) {
                    throw error(
                            MatchErrorCode.BLOCKED_RELATION,
                            "A block relationship prevents joining this casual room");
                }
                match.players.put(PlayerSide.PLAYER_TWO, playerId);
                match.participantCycleStartedAt = receivedAt;
                temporal.openFormation(match, receivedAt);
                match.version++;
                addEvent(match, PublicMatchEvent.Type.PLAYER_JOINED, PlayerSide.PLAYER_TWO,
                        null, null, null, null, null, List.of(), null);
                return remember(match, command.commandId(), fingerprint, playerId,
                        MatchUpdatePublisher.UpdateType.PLAYER_JOINED);
            }
        }
    }

    public MatchCommandResult joinMatch(UUID expectedMatchId, JoinMatchCommand command) {
        PrivateMatch match = byCode(command.roomCode());
        if (!match.id.equals(expectedMatchId)) {
            throw error(MatchErrorCode.MATCH_NOT_FOUND,
                    "Room code does not identify the requested match");
        }
        return joinMatch(command);
    }

    public MatchCommandResult submitFormation(SubmitFormationCommand command) {
        PrivateMatch match = byId(command.matchId());
        synchronized (match) {
            Instant receivedAt = clock.instant();
            PrivateMatch.CommandFingerprint fingerprint = formationFingerprint(command);
            MatchCommandResult replay = replay(match, command.commandId(), fingerprint);
            if (replay != null) {
                return replay;
            }
            temporal.ensureNotExpired(match, receivedAt);
            version(match, command.expectedVersion());
            PlayerSide side = member(match, command.playerId());
            ensureFormationPhase(match);
            if (match.locked.contains(side)) {
                throw error(MatchErrorCode.ALREADY_LOCKED, "Formation is already locked");
            }
            List<Piece> pieces = validateFormation(side, command.pieces());
            Set<UUID> otherIds = new HashSet<>();
            match.formations.entrySet().stream()
                    .filter(entry -> entry.getKey() != side)
                    .flatMap(entry -> entry.getValue().stream())
                    .map(Piece::id)
                    .forEach(otherIds::add);
            if (pieces.stream().map(Piece::id).anyMatch(otherIds::contains)) {
                throw error(MatchErrorCode.INVALID_FORMATION,
                        "Piece identifiers must be unique across the match");
            }
            updatePublicPieceIds(match, side, pieces);
            match.formations.put(side, pieces);
            match.version++;
            return remember(match, command.commandId(), fingerprint, command.playerId(),
                    MatchUpdatePublisher.UpdateType.FORMATION_SUBMITTED);
        }
    }

    public MatchCommandResult lockFormation(LockFormationCommand command) {
        PrivateMatch match = byId(command.matchId());
        synchronized (match) {
            Instant receivedAt = clock.instant();
            PrivateMatch.CommandFingerprint fingerprint = fingerprint("lock",
                    command.playerId(), Long.toString(command.expectedVersion()));
            MatchCommandResult replay = replay(match, command.commandId(), fingerprint);
            if (replay != null) {
                return replay;
            }
            temporal.ensureNotExpired(match, receivedAt);
            version(match, command.expectedVersion());
            PlayerSide side = member(match, command.playerId());
            ensureFormationPhase(match);
            if (match.locked.contains(side)) {
                throw error(MatchErrorCode.ALREADY_LOCKED, "Formation is already locked");
            }
            if (!match.formations.containsKey(side)) {
                throw error(MatchErrorCode.INVALID_FORMATION,
                        "Submit a valid formation before locking");
            }
            match.locked.add(side);
            match.version++;
            addEvent(match, PublicMatchEvent.Type.FORMATION_LOCKED, side,
                    null, null, null, null, null, List.of(), null);
            if (match.locked.size() == 2) {
                start(match, receivedAt);
            }
            MatchUpdatePublisher.UpdateType updateType = match.locked.size() == 2
                    ? MatchUpdatePublisher.UpdateType.MATCH_STARTED
                    : MatchUpdatePublisher.UpdateType.FORMATION_LOCKED;
            return remember(match, command.commandId(), fingerprint, command.playerId(), updateType);
        }
    }

    public MatchCommandResult makeMove(MakeMoveCommand command) {
        PrivateMatch match = byId(command.matchId());
        synchronized (match) {
            Instant receivedAt = clock.instant();
            PrivateMatch.CommandFingerprint fingerprint = fingerprint("move", command.playerId(),
                    Long.toString(command.expectedVersion()), position(command.source()),
                    position(command.destination()));
            MatchCommandResult replay = replay(match, command.commandId(), fingerprint);
            if (replay != null) {
                return replay;
            }
            temporal.ensureNotExpired(match, receivedAt);
            version(match, command.expectedVersion());
            PlayerSide side = member(match, command.playerId());
            if (match.state == null) {
                throw error(MatchErrorCode.INVALID_ROOM_STATE, "Match has not started");
            }
            if (match.state.isTerminal()) {
                throw error(MatchErrorCode.TERMINAL_MATCH, "Match is terminal");
            }
            Move move = new Move(side, command.source(), command.destination());
            if (match.state.pendingFlagChallenge().isPresent()) {
                MoveValidationResult challengeResult = moveValidator.validate(match.state, move);
                if (!challengeResult.valid()
                        && challengeResult.rejectionReason() == MoveRejectionReason.FLAG_CHALLENGE_REQUIRED) {
                    MatchState ordinary = MatchState.active(
                            match.state.board(), side, match.state.acceptedMoveCount(), null);
                    if (moveValidator.validate(ordinary, move).valid()) {
                        PendingFlagChallenge pending = match.state.pendingFlagChallenge().orElseThrow();
                        Piece flag = match.state.board().pieceById(pending.advancedFlagId()).orElseThrow();
                        temporal.freezeActiveClock(match, receivedAt);
                        temporal.finish(match, match.state.board(),
                                TerminalResult.win(flag.owner(), TerminalReason.FLAG_BACK_ROW),
                                match.state.acceptedMoveCount());
                        match.version++;
                        return remember(match, command.commandId(), fingerprint, command.playerId(),
                                MatchUpdatePublisher.UpdateType.MATCH_ENDED);
                    }
                }
            }
            MoveValidationResult validation = moveValidator.validate(match.state, move);
            if (!validation.valid()) {
                throw error(MatchErrorCode.ILLEGAL_MOVE, validation.rejectionReason().name());
            }
            long remainingBeforeIncrement = temporal.chargeAcceptedTurn(match, side, receivedAt);
            applyMove(match, move);
            if (!match.state.isTerminal()
                    && match.timerMode == TimerMode.STANDARD_15_PLUS_5) {
                match.remainingMillis.put(
                        side,
                        remainingBeforeIncrement + timing.moveIncrement().toMillis());
                temporal.beginTurn(match, receivedAt);
            }
            match.version++;
            return remember(match, command.commandId(), fingerprint, command.playerId(),
                    moveUpdateType(match));
        }
    }

    public MatchCommandResult resign(ResignCommand command) {
        PrivateMatch match = byId(command.matchId());
        synchronized (match) {
            Instant receivedAt = clock.instant();
            PrivateMatch.CommandFingerprint fingerprint = fingerprint("resign",
                    command.playerId(), Long.toString(command.expectedVersion()));
            MatchCommandResult replay = replay(match, command.commandId(), fingerprint);
            if (replay != null) {
                return replay;
            }
            temporal.ensureNotExpired(match, receivedAt);
            version(match, command.expectedVersion());
            PlayerSide side = member(match, command.playerId());
            if (match.state == null) {
                throw error(MatchErrorCode.INVALID_ROOM_STATE, "Match has not started");
            }
            if (match.state.isTerminal()) {
                throw error(MatchErrorCode.TERMINAL_MATCH, "Match is terminal");
            }
            TerminalResult result = TerminalResult.win(side.opponent(), TerminalReason.RESIGNATION);
            temporal.freezeActiveClock(match, receivedAt);
            addEvent(match, PublicMatchEvent.Type.PLAYER_RESIGNED, side,
                    null, null, null, null, null, List.of(), result);
            temporal.finish(match, match.state.board(), result, match.state.acceptedMoveCount());
            match.version++;
            return remember(match, command.commandId(), fingerprint, command.playerId(),
                    MatchUpdatePublisher.UpdateType.MATCH_ENDED);
        }
    }

    public MatchLifecycleResult cancelMatch(CancelMatchCommand command) {
        PrivateMatch match = byId(command.matchId());
        synchronized (match) {
            PrivateMatch.CommandFingerprint fingerprint = fingerprint(
                    "cancel", command.playerId(), Long.toString(command.expectedVersion()));
            MatchLifecycleResult replay = replayLifecycle(
                    match, command.commandId(), fingerprint);
            if (replay != null) {
                return replay;
            }
            temporal.ensureNotExpired(match, clock.instant());
            version(match, command.expectedVersion());
            PlayerSide side = member(match, command.playerId());
            if (side != PlayerSide.PLAYER_ONE) {
                throw error(MatchErrorCode.INVALID_ROOM_STATE,
                        "Only the private-room host may cancel the room");
            }
            return cancelPrivateRoom(match, command.commandId(), fingerprint);
        }
    }

    public MatchLifecycleResult leaveMatch(LeaveMatchCommand command) {
        PrivateMatch match = byId(command.matchId());
        synchronized (match) {
            PrivateMatch.CommandFingerprint fingerprint = fingerprint(
                    "leave", command.playerId(), Long.toString(command.expectedVersion()));
            MatchLifecycleResult replay = replayLifecycle(
                    match, command.commandId(), fingerprint);
            if (replay != null) {
                return replay;
            }
            temporal.ensureNotExpired(match, clock.instant());
            version(match, command.expectedVersion());
            PlayerSide side = member(match, command.playerId());
            if (side == PlayerSide.PLAYER_ONE) {
                return cancelPrivateRoom(match, command.commandId(), fingerprint);
            }
            requireCasualFormation(match);
            if (!match.locked.isEmpty()) {
                throw error(MatchErrorCode.INVALID_ROOM_STATE,
                        "A locked formation cannot be removed; resume the match instead");
            }

            List<Piece> departingFormation = match.formations.remove(PlayerSide.PLAYER_TWO);
            if (departingFormation != null) {
                departingFormation.forEach(piece -> match.publicPieceIds.remove(piece.id()));
            }
            match.players.remove(PlayerSide.PLAYER_TWO);
            match.locked.remove(PlayerSide.PLAYER_TWO);
            match.connected.remove(PlayerSide.PLAYER_TWO);
            match.disconnectedSince.remove(PlayerSide.PLAYER_TWO);
            match.cumulativeDisconnectedMillis.remove(PlayerSide.PLAYER_TWO);
            match.remainingMillis.remove(PlayerSide.PLAYER_TWO);
            match.participantCycleStartedAt = null;
            match.formationDeadline = null;
            match.turnStartedAt = null;
            match.turnDeadline = null;
            match.version++;
            addEvent(match, PublicMatchEvent.Type.PLAYER_LEFT, PlayerSide.PLAYER_TWO,
                    null, null, null, null, null, List.of(), null);
            return rememberLifecycle(
                    match,
                    command.commandId(),
                    fingerprint,
                    MatchLifecycleResult.Action.LOBBY_LEFT,
                    MatchUpdatePublisher.UpdateType.PLAYER_LEFT);
        }
    }

    public PlayerMatchView getView(UUID matchId, String playerId) {
        PrivateMatch match = byId(matchId);
        synchronized (match) {
            temporal.evaluateDeadlines(match, clock.instant());
            return mapper.map(match, playerId);
        }
    }

    public PlayerMatchView getViewByRoomCode(String code, String playerId) {
        PrivateMatch match = byCode(code);
        synchronized (match) {
            temporal.evaluateDeadlines(match, clock.instant());
            return mapper.map(match, playerId);
        }
    }

    public List<String> participantIds(UUID matchId, String requestingPlayerId) {
        PrivateMatch match = byId(matchId);
        synchronized (match) {
            member(match, requestingPlayerId);
            return List.copyOf(match.players.values());
        }
    }

    public Instant participantCycleStartedAt(UUID matchId, String requestingPlayerId) {
        PrivateMatch match = byId(matchId);
        synchronized (match) {
            member(match, requestingPlayerId);
            return match.participantCycleStartedAt;
        }
    }

    public MatchSummary summary(UUID matchId) {
        PrivateMatch match = byId(matchId);
        synchronized (match) {
            return summaryOf(match);
        }
    }

    public List<MatchSummary> summaries() {
        return repository.findAll().stream()
                .map(match -> {
                    synchronized (match) {
                        return summaryOf(match);
                    }
                })
                .toList();
    }

    public List<CurrentMatchSummary> currentMatches(String playerId) {
        List<CurrentMatchSummary> current = new ArrayList<>();
        for (PrivateMatch match : repository.findAll()) {
            synchronized (match) {
                temporal.evaluateDeadlines(match, clock.instant());
                PlayerSide side = match.sideOf(playerId);
                if (side != null && !terminal(match)) {
                    current.add(currentSummary(match, side));
                }
            }
        }
        current.sort(Comparator.comparing(
                CurrentMatchSummary::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(current);
    }

    public boolean hasActiveMatch(String playerId) {
        return !currentMatches(playerId).isEmpty();
    }

    public Optional<MatchSummary> activeRankedMatch(String playerId) {
        return summaries().stream()
                .filter(summary -> summary.mode() == MatchMode.RANKED)
                .filter(summary -> summary.phase() != MatchPhase.TERMINAL)
                .filter(summary -> summary.participants().containsValue(playerId))
                .findFirst();
    }

    private MatchSummary summaryOf(PrivateMatch match) {
        MatchPhase phase = match.state == null ? MatchPhase.FORMATION : match.state.phase();
        TerminalResult terminalResult = match.state == null
                ? null
                : match.state.terminalResult().orElse(null);
        int acceptedMoveCount = match.state == null ? 0 : match.state.acceptedMoveCount();
        return new MatchSummary(
                match.id,
                match.mode,
                match.timerMode,
                phase,
                Map.copyOf(match.players),
                terminalResult,
                acceptedMoveCount);
    }

    public record MatchSummary(
            UUID matchId,
            MatchMode mode,
            TimerMode timerMode,
            MatchPhase phase,
            Map<PlayerSide, String> participants,
            TerminalResult terminalResult,
            int acceptedMoveCount) {
    }

    public record RankedMatch(
            UUID matchId,
            PlayerMatchView playerOneView,
            PlayerMatchView playerTwoView) {
    }

    public record CurrentMatchSummary(
            UUID matchId,
            MatchMode mode,
            MatchPhase phase,
            long version,
            String roomCode,
            PlayerSide side,
            boolean opponentPresent,
            boolean ownFormationSubmitted,
            boolean ownLocked,
            boolean opponentLocked,
            PlayerSide currentPlayer,
            Instant createdAt,
            Instant updatedAt,
            boolean canResume,
            boolean canCancel,
            boolean canLeave,
            String resumeRoute) {
    }

    private void applyMove(PrivateMatch match, Move move) {
        Board board = match.state.board();
        Piece attacker = board.pieceAt(move.source()).orElseThrow();
        Piece defender = board.pieceAt(move.destination()).orElse(null);
        int acceptedMoveCount = match.state.acceptedMoveCount() + 1;
        Piece movedPiece = null;
        BattleResult battle = null;
        List<UUID> removedIds = new ArrayList<>();

        if (defender == null) {
            movedPiece = attacker.moveTo(move.destination());
            board = board.replace(movedPiece);
        } else {
            battle = battleResolver.resolve(attacker, defender);
            switch (battle.outcome()) {
                case ATTACKER_SURVIVES -> {
                    movedPiece = attacker.moveTo(move.destination());
                    board = board.replace(movedPiece, defender.remove());
                    removedIds.add(defender.id());
                }
                case DEFENDER_SURVIVES -> {
                    board = board.replace(attacker.remove());
                    removedIds.add(attacker.id());
                }
                case BOTH_REMOVED -> {
                    board = board.replace(attacker.remove(), defender.remove());
                    removedIds.add(attacker.id());
                    removedIds.add(defender.id());
                }
            }
        }

        addEvent(match, PublicMatchEvent.Type.MOVE_APPLIED, move.actingPlayer(),
                move.source(), move.destination(), attacker.id(),
                defender == null ? null : defender.id(), null, List.of(), null);
        if (battle != null) {
            addEvent(match, PublicMatchEvent.Type.BATTLE_RESOLVED, move.actingPlayer(),
                    move.source(), move.destination(), attacker.id(), defender.id(),
                    battle.outcome(), removedIds, null);
        }

        VictoryEvaluation evaluation = battle == null
                ? VictoryEvaluation.ongoing()
                : victoryEvaluator.afterBattle(move.actingPlayer(), battle);
        PendingFlagChallenge prospectiveChallenge = null;
        if (evaluation.result().isEmpty() && movedPiece != null) {
            evaluation = victoryEvaluator.afterMove(board, movedPiece, acceptedMoveCount, 1);
            prospectiveChallenge = evaluation.challenge().orElse(null);
        }
        int occurrences = recordPosition(
                match, board, move.actingPlayer().opponent(), prospectiveChallenge);
        if (evaluation.result().isEmpty() && movedPiece != null) {
            evaluation = victoryEvaluator.afterMove(
                    board, movedPiece, acceptedMoveCount, occurrences);
        }
        if (evaluation.result().isPresent()) {
            temporal.finish(match, board, evaluation.result().orElseThrow(), acceptedMoveCount);
            return;
        }

        PendingFlagChallenge challenge = evaluation.challenge().orElse(null);
        if (challenge != null) {
            addEvent(match, PublicMatchEvent.Type.FLAG_CHALLENGE_STARTED, move.actingPlayer(),
                    null, challenge.flagPosition(), null, null, null, List.of(), null);
        }
        match.state = MatchState.active(
                board, move.actingPlayer().opponent(), acceptedMoveCount, challenge, occurrences);
        VictoryEvaluation turnStart = victoryEvaluator.atTurnStart(match.state, moveValidator);
        if (turnStart.result().isPresent()) {
            temporal.finish(match, board, turnStart.result().orElseThrow(), acceptedMoveCount);
        }
    }

    private void start(PrivateMatch match, Instant now) {
        List<Piece> allPieces = new ArrayList<>();
        match.formations.values().forEach(allPieces::addAll);
        PlayerSide firstPlayer = random.nextBoolean()
                ? PlayerSide.PLAYER_ONE
                : PlayerSide.PLAYER_TWO;
        match.state = MatchState.active(new Board(allPieces), firstPlayer);
        temporal.startPlay(match, now);
        recordPosition(match, match.state.board(), firstPlayer, null);
        addEvent(match, PublicMatchEvent.Type.MATCH_STARTED, firstPlayer,
                null, null, null, null, null, List.of(), null);
    }

    private int recordPosition(
            PrivateMatch match, Board board, PlayerSide currentPlayer,
            PendingFlagChallenge challenge) {
        StringJoiner identity = new StringJoiner("|");
        identity.add(currentPlayer.name());
        identity.add(canonicalChallenge(challenge));
        board.pieces().stream()
                .sorted(Comparator.comparing(Piece::id))
                .map(this::canonicalPieceState)
                .forEach(identity::add);
        return match.repetitions.merge(identity.toString(), 1, Integer::sum);
    }

    private String canonicalPieceState(Piece piece) {
        return piece.id() + ":" + piece.owner().name() + ":" + piece.rank().name()
                + ":" + piece.position().map(this::position).orElse("removed");
    }

    private String canonicalChallenge(PendingFlagChallenge challenge) {
        if (challenge == null) {
            return "no-challenge";
        }
        String challengers = challenge.eligibleChallengerIds().stream()
                .sorted()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(","));
        return challenge.advancedFlagId() + ":" + position(challenge.flagPosition()) + ":"
                + challenge.respondingPlayer().name() + ":" + challengers;
    }

    private List<Piece> validateFormation(PlayerSide side, List<FormationPiece> submitted) {
        if (submitted == null || submitted.size() != 21) {
            throw error(MatchErrorCode.INVALID_FORMATION,
                    "Formation must contain exactly 21 pieces");
        }
        Set<UUID> ids = new HashSet<>();
        Set<Position> positions = new HashSet<>();
        EnumMap<Rank, Integer> counts = new EnumMap<>(Rank.class);
        List<Piece> result = new ArrayList<>();
        for (FormationPiece item : submitted) {
            if (item == null || item.pieceId() == null
                    || item.rank() == null || item.position() == null) {
                throw error(MatchErrorCode.INVALID_FORMATION,
                        "Every piece requires id, rank, and position");
            }
            if (!ids.add(item.pieceId())) {
                throw error(MatchErrorCode.INVALID_FORMATION, "Duplicate piece identifier");
            }
            if (!positions.add(item.position())) {
                throw error(MatchErrorCode.INVALID_FORMATION, "Duplicate position");
            }
            int row = item.position().row();
            boolean validDeploymentRow = side == PlayerSide.PLAYER_ONE ? row <= 2 : row >= 5;
            if (!validDeploymentRow) {
                throw error(MatchErrorCode.INVALID_FORMATION,
                        "Piece is outside deployment rows");
            }
            counts.merge(item.rank(), 1, Integer::sum);
            result.add(Piece.at(item.pieceId(), side, item.rank(), item.position()));
        }
        if (!counts.equals(INVENTORY)) {
            throw error(MatchErrorCode.INVALID_FORMATION,
                    "Formation inventory does not match the required 21 pieces");
        }
        return List.copyOf(result);
    }
    private void updatePublicPieceIds(PrivateMatch match, PlayerSide side, List<Piece> pieces) {
        Set<UUID> currentSideIds = match.formations.getOrDefault(side, List.of()).stream()
                .map(Piece::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> submittedIds = pieces.stream()
                .map(Piece::id)
                .collect(java.util.stream.Collectors.toSet());
        currentSideIds.stream()
                .filter(id -> !submittedIds.contains(id))
                .forEach(match.publicPieceIds::remove);

        Set<UUID> unavailableIds = new HashSet<>(match.publicPieceIds.values());
        match.formations.values().stream()
                .flatMap(List::stream)
                .map(Piece::id)
                .forEach(unavailableIds::add);
        pieces.stream().map(Piece::id).forEach(unavailableIds::add);
        for (Piece piece : pieces) {
            match.publicPieceIds.computeIfAbsent(piece.id(), ignored -> opaquePieceId(unavailableIds));
        }
    }

    private UUID opaquePieceId(Set<UUID> unavailableIds) {
        UUID publicId;
        do {
            publicId = UUID.randomUUID();
        } while (!unavailableIds.add(publicId));
        return publicId;
    }

    private PrivateMatch.CommandFingerprint formationFingerprint(SubmitFormationCommand command) {
        List<String> canonicalPieces = command.pieces() == null
                ? List.of("<null-list>")
                : command.pieces().stream()
                        .map(this::canonicalFormationPiece)
                        .sorted()
                        .toList();
        List<String> components = new ArrayList<>();
        components.add(nullable(command.playerId()));
        components.add(Long.toString(command.expectedVersion()));
        components.addAll(canonicalPieces);
        return new PrivateMatch.CommandFingerprint("formation", components);
    }

    private String canonicalFormationPiece(FormationPiece piece) {
        if (piece == null) {
            return "<null-piece>";
        }
        return nullable(piece.pieceId()) + ":" + nullable(piece.rank()) + ":" + position(piece.position());
    }

    private String position(Position position) {
        return position == null ? "<null-position>" : position.row() + "," + position.column();
    }

    private String nullable(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Unsupported fingerprint value type: " + value.getClass());
    }

    private String canonicalRoomCode(String roomCode) {
        return roomCode == null ? "<null>" : roomCode.toUpperCase(Locale.ROOT);
    }

    private PrivateMatch.CommandFingerprint fingerprint(String operation, String... components) {
        return new PrivateMatch.CommandFingerprint(operation, List.of(components));
    }

    public void playerConnected(UUID matchId, String playerId) {
        temporal.playerConnected(matchId, playerId);
    }

    public void playerDisconnected(UUID matchId, String playerId) {
        temporal.playerDisconnected(matchId, playerId);
    }

    public void evaluateAllDeadlines() {
        temporal.evaluateAllDeadlines();
    }

    public void publishTimerSyncs() {
        temporal.publishTimerSyncs();
    }

    public void recoverAfterRestart() {
        temporal.recoverAfterRestart();
    }

    private MatchCommandResult remember(
            PrivateMatch match, UUID commandId, PrivateMatch.CommandFingerprint fingerprint,
            String playerId, MatchUpdatePublisher.UpdateType updateType) {
        MatchCommandResult result = new MatchCommandResult(
                commandId, match.version, mapper.map(match, playerId));
        match.commands.put(commandId, PrivateMatch.StoredCommand.match(fingerprint, result));
        match.updatedAt = clock.instant();
        while (match.commands.size() > COMMAND_HISTORY_LIMIT) {
            match.commands.remove(match.commands.keySet().iterator().next());
        }
        match.liveSequence++;
        repository.save(match);
        publishUpdate(match, updateType);
        return result;
    }

    private MatchLifecycleResult rememberLifecycle(
            PrivateMatch match,
            UUID commandId,
            PrivateMatch.CommandFingerprint fingerprint,
            MatchLifecycleResult.Action action,
            MatchUpdatePublisher.UpdateType updateType) {
        MatchLifecycleResult result = new MatchLifecycleResult(
                commandId, match.version, match.id, action);
        match.commands.put(commandId, PrivateMatch.StoredCommand.lifecycle(fingerprint, result));
        match.updatedAt = clock.instant();
        while (match.commands.size() > COMMAND_HISTORY_LIMIT) {
            match.commands.remove(match.commands.keySet().iterator().next());
        }
        match.liveSequence++;
        repository.save(match);
        publishUpdate(match, updateType);
        return result;
    }

    private MatchUpdatePublisher.UpdateType moveUpdateType(PrivateMatch match) {
        if (match.state.isTerminal()) {
            return MatchUpdatePublisher.UpdateType.MATCH_ENDED;
        }
        PublicMatchEvent.Type latestType = match.events.get(match.events.size() - 1).type();
        return switch (latestType) {
            case BATTLE_RESOLVED -> MatchUpdatePublisher.UpdateType.BATTLE_RESOLVED;
            case FLAG_CHALLENGE_STARTED -> MatchUpdatePublisher.UpdateType.FLAG_CHALLENGE_STARTED;
            default -> MatchUpdatePublisher.UpdateType.MOVE_APPLIED;
        };
    }

    private void publishUpdate(
            PrivateMatch match, MatchUpdatePublisher.UpdateType updateType) {
        Map<String, PlayerMatchView> playerViews = new HashMap<>();
        match.players.values().forEach(playerId ->
                playerViews.put(playerId, mapper.map(match, playerId)));
        try {
            updatePublisher.publish(new MatchUpdatePublisher.MatchUpdate(
                    match.id, match.liveSequence, updateType, playerViews));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Match {} version {} was persisted but its live update could not be published",
                    match.id,
                    match.version,
                    exception);
        }
    }

    private MatchCommandResult replay(
            PrivateMatch match, UUID commandId, PrivateMatch.CommandFingerprint fingerprint) {
        if (commandId == null) {
            throw new IllegalArgumentException("commandId is required");
        }
        PrivateMatch.StoredCommand previous = match.commands.get(commandId);
        if (previous == null) {
            return null;
        }
        if (!previous.fingerprint().equals(fingerprint)) {
            throw error(MatchErrorCode.COMMAND_CONFLICT,
                    "commandId was already used with a different payload");
        }
        if (previous.result() == null) {
            throw error(MatchErrorCode.COMMAND_CONFLICT,
                    "commandId was already used for another result type");
        }
        return previous.result();
    }

    private MatchLifecycleResult replayLifecycle(
            PrivateMatch match,
            UUID commandId,
            PrivateMatch.CommandFingerprint fingerprint) {
        if (commandId == null) {
            throw new IllegalArgumentException("commandId is required");
        }
        PrivateMatch.StoredCommand previous = match.commands.get(commandId);
        if (previous == null) {
            return null;
        }
        if (!previous.fingerprint().equals(fingerprint)) {
            throw error(MatchErrorCode.COMMAND_CONFLICT,
                    "commandId was already used with a different payload");
        }
        if (previous.lifecycleResult() == null) {
            throw error(MatchErrorCode.COMMAND_CONFLICT,
                    "commandId was already used for another result type");
        }
        return previous.lifecycleResult();
    }

    private void version(PrivateMatch match, long expectedVersion) {
        if (match.version != expectedVersion) {
            throw error(MatchErrorCode.STALE_VERSION,
                    "Expected version " + expectedVersion + " but current version is " + match.version);
        }
    }

    private PlayerSide member(PrivateMatch match, String playerId) {
        PlayerSide side = match.sideOf(playerId);
        if (side == null) {
            throw error(MatchErrorCode.PLAYER_NOT_IN_MATCH, "Player does not belong to match");
        }
        return side;
    }

    private void ensureFormationPhase(PrivateMatch match) {
        if (match.state != null) {
            MatchErrorCode code = match.state.isTerminal()
                    ? MatchErrorCode.TERMINAL_MATCH
                    : MatchErrorCode.INVALID_ROOM_STATE;
            throw error(code, "Formation phase has ended");
        }
    }

    private MatchLifecycleResult cancelPrivateRoom(
            PrivateMatch match,
            UUID commandId,
            PrivateMatch.CommandFingerprint fingerprint) {
        requireCasualFormation(match);
        TerminalResult result = TerminalResult.draw(TerminalReason.ROOM_CANCELLED);
        addEvent(match, PublicMatchEvent.Type.ROOM_CANCELLED, PlayerSide.PLAYER_ONE,
                null, null, null, null, null, List.of(), result);
        temporal.finish(match, Board.empty(), result, 0);
        match.version++;
        return rememberLifecycle(
                match,
                commandId,
                fingerprint,
                MatchLifecycleResult.Action.ROOM_CANCELLED,
                MatchUpdatePublisher.UpdateType.ROOM_CANCELLED);
    }

    private void requireCasualFormation(PrivateMatch match) {
        if (match.mode == MatchMode.RANKED) {
            throw error(MatchErrorCode.INVALID_ROOM_STATE,
                    "Ranked matches must be resumed and cannot use casual lobby actions");
        }
        if (match.state != null) {
            MatchErrorCode code = match.state.isTerminal()
                    ? MatchErrorCode.TERMINAL_MATCH
                    : MatchErrorCode.INVALID_ROOM_STATE;
            throw error(code, "Active matches must be resumed or resigned");
        }
    }

    private CurrentMatchSummary currentSummary(PrivateMatch match, PlayerSide side) {
        PlayerSide opponent = side.opponent();
        boolean formationPhase = match.state == null;
        boolean canCancel = formationPhase
                && match.mode == MatchMode.CASUAL
                && side == PlayerSide.PLAYER_ONE;
        boolean canLeave = formationPhase
                && match.mode == MatchMode.CASUAL
                && side == PlayerSide.PLAYER_TWO
                && match.locked.isEmpty();
        return new CurrentMatchSummary(
                match.id,
                match.mode,
                match.state == null ? MatchPhase.FORMATION : match.state.phase(),
                match.version,
                match.roomCode,
                side,
                match.players.containsKey(opponent),
                match.formations.containsKey(side),
                match.locked.contains(side),
                match.locked.contains(opponent),
                match.state == null ? null : match.state.currentPlayer().orElse(null),
                match.createdAt,
                match.updatedAt,
                true,
                canCancel,
                canLeave,
                "/matches/" + match.id);
    }

    private void requireNoOpenMatch(String playerId) {
        List<CurrentMatchSummary> current = currentMatches(playerId);
        if (!current.isEmpty()) {
            throw error(MatchErrorCode.OPEN_MATCH_EXISTS,
                    "Resume or close the existing match before entering another one");
        }
    }

    private boolean terminal(PrivateMatch match) {
        return match.state != null && match.state.isTerminal();
    }

    private PrivateMatch byId(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> error(MatchErrorCode.MATCH_NOT_FOUND, "Match not found"));
    }

    private PrivateMatch byCode(String code) {
        return repository.findByRoomCode(code)
                .orElseThrow(() -> error(MatchErrorCode.MATCH_NOT_FOUND, "Room code not found"));
    }

    private MatchApplicationException error(MatchErrorCode code, String message) {
        return new MatchApplicationException(code, message);
    }

    private void addEvent(
            PrivateMatch match, PublicMatchEvent.Type type, PlayerSide actor,
            Position source, Position destination, UUID attackerId, UUID defenderId,
            BattleOutcome battleOutcome, List<UUID> removedIds, TerminalResult terminalResult) {
        match.events.add(new PublicMatchEvent(
                match.nextEventSequence++, type, actor, source, destination, attackerId,
                defenderId, battleOutcome, removedIds, terminalResult));
    }

    private String roomCode() {
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(ROOM_CHARS[random.nextInt(ROOM_CHARS.length)]);
        }
        return code.toString();
    }

    private String opaquePlayerId() {
        return "dev_" + UUID.randomUUID();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
