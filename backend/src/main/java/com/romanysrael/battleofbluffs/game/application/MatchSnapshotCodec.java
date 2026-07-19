package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.Board;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.MatchState;
import com.romanysrael.battleofbluffs.game.domain.PendingFlagChallenge;
import com.romanysrael.battleofbluffs.game.domain.Piece;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public final class MatchSnapshotCodec {
    private final ObjectMapper json;

    public MatchSnapshotCodec(ObjectMapper json) {
        this.json = json;
    }

    public String encode(PrivateMatch match) {
        Snapshot snapshot = new Snapshot(
                match.id,
                match.roomCode,
                match.mode,
                match.timerMode,
                match.version,
                match.nextEventSequence,
                Map.copyOf(match.players),
                formations(match.formations),
                Set.copyOf(match.locked),
                List.copyOf(match.events),
                commands(match.commands),
                Map.copyOf(match.repetitions),
                Map.copyOf(match.publicPieceIds),
                state(match.state),
                match.liveSequence,
                Map.copyOf(match.remainingMillis),
                Set.copyOf(match.connected),
                Map.copyOf(match.disconnectedSince),
                Map.copyOf(match.cumulativeDisconnectedMillis),
                match.formationDeadline,
                match.turnStartedAt,
                match.turnDeadline,
                match.createdAt,
                match.updatedAt,
                match.participantCycleStartedAt);
        return write(snapshot);
    }

    public PrivateMatch decode(String encoded) {
        try {
            Snapshot snapshot = json.readValue(encoded, Snapshot.class);
            String creator = snapshot.players().get(PlayerSide.PLAYER_ONE);
            MatchMode mode = snapshot.mode() == null ? MatchMode.CASUAL : snapshot.mode();
            TimerMode timerMode = snapshot.timerMode() == null
                    ? TimerMode.CASUAL_UNTIMED
                    : snapshot.timerMode();
            PrivateMatch match = new PrivateMatch(
                    snapshot.id(), snapshot.roomCode(), creator, mode, timerMode);
            match.version = snapshot.version();
            match.liveSequence = snapshot.liveSequence() != null && snapshot.liveSequence() > 0
                    ? snapshot.liveSequence()
                    : snapshot.version();
            match.nextEventSequence = snapshot.nextEventSequence();
            match.players.clear();
            match.players.putAll(snapshot.players());
            snapshot.formations().forEach((side, pieces) ->
                    match.formations.put(side, pieces.stream().map(this::piece).toList()));
            match.locked.addAll(snapshot.locked());
            match.events.addAll(snapshot.events());
            snapshot.commands().forEach(command -> match.commands.put(
                    command.commandId(),
                    new PrivateMatch.StoredCommand(
                            new PrivateMatch.CommandFingerprint(
                                    command.operation(), command.components()),
                            command.result(),
                            command.lifecycleResult())));
            match.repetitions.putAll(snapshot.repetitions());
            match.publicPieceIds.putAll(snapshot.publicPieceIds());
            match.state = restoreState(snapshot.state());
            if (snapshot.remainingMillis() != null) {
                match.remainingMillis.putAll(snapshot.remainingMillis());
            }
            if (snapshot.connected() != null) {
                match.connected.addAll(snapshot.connected());
            }
            if (snapshot.disconnectedSince() != null) {
                match.disconnectedSince.putAll(snapshot.disconnectedSince());
            }
            if (snapshot.cumulativeDisconnectedMillis() != null) {
                match.cumulativeDisconnectedMillis.putAll(
                        snapshot.cumulativeDisconnectedMillis());
            }
            match.formationDeadline = snapshot.formationDeadline();
            match.turnStartedAt = snapshot.turnStartedAt();
            match.turnDeadline = snapshot.turnDeadline();
            match.participantCycleStartedAt = snapshot.participantCycleStartedAt();
            if (snapshot.createdAt() != null) {
                match.createdAt = snapshot.createdAt();
            }
            if (snapshot.updatedAt() != null) {
                match.updatedAt = snapshot.updatedAt();
            }
            return match;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persisted match snapshot is invalid", exception);
        }
    }

    public String encodeEvent(PublicMatchEvent event) {
        return write(event);
    }

    public String encodeResult(MatchCommandResult result) {
        return write(result);
    }

    public String encodeLifecycleResult(MatchLifecycleResult result) {
        return write(result);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not encode match snapshot", exception);
        }
    }

    private Map<PlayerSide, List<PieceData>> formations(
            EnumMap<PlayerSide, List<Piece>> formations) {
        Map<PlayerSide, List<PieceData>> result = new EnumMap<>(PlayerSide.class);
        formations.forEach((side, pieces) -> result.put(
                side, pieces.stream().map(this::pieceData).toList()));
        return result;
    }

    private List<StoredCommandData> commands(
            LinkedHashMap<UUID, PrivateMatch.StoredCommand> commands) {
        List<StoredCommandData> result = new ArrayList<>();
        commands.forEach((commandId, command) -> result.add(new StoredCommandData(
                commandId,
                command.fingerprint().operation(),
                command.fingerprint().components(),
                command.result(),
                command.lifecycleResult())));
        return result;
    }

    private StateData state(MatchState state) {
        if (state == null) {
            return null;
        }
        return new StateData(
                state.phase(),
                state.currentPlayer().orElse(null),
                state.terminalResult().orElse(null),
                state.pendingFlagChallenge().orElse(null),
                state.acceptedMoveCount(),
                state.currentPositionOccurrences(),
                state.board().pieces().stream().map(this::pieceData).toList());
    }

    private MatchState restoreState(StateData state) {
        if (state == null) {
            return null;
        }
        Board board = new Board(state.board().stream().map(this::piece).toList());
        if (state.phase() == MatchPhase.TERMINAL) {
            return MatchState.terminal(board, state.terminalResult(), state.acceptedMoveCount());
        }
        return MatchState.active(
                board,
                state.currentPlayer(),
                state.acceptedMoveCount(),
                state.pendingFlagChallenge(),
                state.currentPositionOccurrences());
    }

    private PieceData pieceData(Piece piece) {
        Position position = piece.position().orElse(null);
        return new PieceData(piece.id(), piece.owner(), piece.rank(), position);
    }

    private Piece piece(PieceData data) {
        return data.position() == null
                ? Piece.removed(data.id(), data.owner(), data.rank())
                : Piece.at(data.id(), data.owner(), data.rank(), data.position());
    }

    public record Snapshot(
            UUID id,
            String roomCode,
            MatchMode mode,
            TimerMode timerMode,
            long version,
            long nextEventSequence,
            Map<PlayerSide, String> players,
            Map<PlayerSide, List<PieceData>> formations,
            Set<PlayerSide> locked,
            List<PublicMatchEvent> events,
            List<StoredCommandData> commands,
            Map<String, Integer> repetitions,
            Map<UUID, UUID> publicPieceIds,
            StateData state,
            Long liveSequence,
            Map<PlayerSide, Long> remainingMillis,
            Set<PlayerSide> connected,
            Map<PlayerSide, Instant> disconnectedSince,
            Map<PlayerSide, Long> cumulativeDisconnectedMillis,
            Instant formationDeadline,
            Instant turnStartedAt,
            Instant turnDeadline,
            Instant createdAt,
            Instant updatedAt,
            Instant participantCycleStartedAt) { }

    public record PieceData(UUID id, PlayerSide owner, Rank rank, Position position) { }

    public record StoredCommandData(
            UUID commandId,
            String operation,
            List<String> components,
            MatchCommandResult result,
            MatchLifecycleResult lifecycleResult) { }

    public record StateData(
            MatchPhase phase,
            PlayerSide currentPlayer,
            TerminalResult terminalResult,
            PendingFlagChallenge pendingFlagChallenge,
            int acceptedMoveCount,
            int currentPositionOccurrences,
            List<PieceData> board) { }
}
