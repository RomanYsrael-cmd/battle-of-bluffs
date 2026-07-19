package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class PlayerMatchViewMapper {
    private Clock clock = Clock.systemUTC();
    private MatchTimingSettings timing = MatchTimingSettings.defaults();

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Autowired(required = false)
    void setTiming(MatchTimingSettings timing) {
        this.timing = timing;
    }

    public PlayerMatchView map(PrivateMatch match, String playerId) {
        PlayerSide side = match.sideOf(playerId);
        if (side == null) {
            throw new MatchApplicationException(
                    MatchErrorCode.PLAYER_NOT_IN_MATCH, "Player does not belong to this match");
        }
        Collection<Piece> pieces = match.state == null
                ? match.formations.values().stream().flatMap(List::stream).toList()
                : match.state.board().pieces();
        Map<UUID, Piece> piecesById = pieces.stream()
                .collect(java.util.stream.Collectors.toMap(Piece::id, piece -> piece));
        List<PlayerMatchView.OwnPieceView> own = pieces.stream()
                .filter(piece -> piece.owner() == side)
                .map(piece -> new PlayerMatchView.OwnPieceView(
                        piece.id(), piece.rank(), piece.position().orElse(null), piece.isAlive()))
                .toList();
        List<PlayerMatchView.OpponentPieceView> opponent = pieces.stream()
                .filter(piece -> piece.owner() != side && piece.isAlive())
                .map(piece -> new PlayerMatchView.OpponentPieceView(
                        publicId(match, piece.id()), piece.position().orElseThrow()))
                .toList();
        boolean terminal = match.state != null && match.state.isTerminal();
        List<PlayerMatchView.RevealedPieceView> revealed = terminal ? pieces.stream()
                .map(piece -> new PlayerMatchView.RevealedPieceView(
                        idForViewer(match, piece, side), piece.owner(), piece.rank(),
                        piece.position().orElse(null), piece.isAlive()))
                .toList() : List.of();
        List<PlayerMatchView.EventView> events = match.events.stream()
                .map(event -> eventFor(match, event, side, piecesById))
                .toList();
        PendingFlagChallenge pending = match.state == null ? null : match.state.pendingFlagChallenge().orElse(null);
        PlayerMatchView.PendingChallengeView challenge = pending == null ? null : new PlayerMatchView.PendingChallengeView(
                idForViewer(match, piecesById.get(pending.advancedFlagId()), side),
                pending.flagPosition(), pending.respondingPlayer(),
                pending.respondingPlayer() == side ? pending.eligibleChallengerIds() : Set.of());
        Instant now = clock.instant();
        return new PlayerMatchView(match.id, match.roomCode, match.version, match.liveSequence,
                match.state == null ? MatchPhase.FORMATION : match.state.phase(),
                match.mode, match.timerMode, playerId, side,
                match.players.containsKey(PlayerSide.PLAYER_ONE), match.players.containsKey(PlayerSide.PLAYER_TWO),
                match.locked.contains(PlayerSide.PLAYER_ONE), match.locked.contains(PlayerSide.PLAYER_TWO),
                match.state == null ? null : match.state.currentPlayer().orElse(null), own, opponent, events, challenge,
                match.state == null ? null : match.state.terminalResult().orElse(null), revealed,
                timer(match, now), presence(match, now));
    }

    private PlayerMatchView.TimerView timer(PrivateMatch match, Instant now) {
        return new PlayerMatchView.TimerView(
                remaining(match, PlayerSide.PLAYER_ONE, now),
                remaining(match, PlayerSide.PLAYER_TWO, now),
                match.formationDeadline,
                match.turnDeadline,
                match.timerMode == TimerMode.STANDARD_15_PLUS_5
                        ? timing.moveIncrement().toMillis()
                        : 0,
                now);
    }

    private long remaining(PrivateMatch match, PlayerSide side, Instant now) {
        long stored = match.remainingMillis.getOrDefault(side, 0L);
        if (match.timerMode != TimerMode.STANDARD_15_PLUS_5
                || match.state == null
                || match.state.isTerminal()
                || match.state.currentPlayer().orElse(null) != side
                || match.turnStartedAt == null) {
            return stored;
        }
        long elapsed = Math.max(0, Duration.between(match.turnStartedAt, now).toMillis());
        return Math.max(0, stored - elapsed);
    }

    private PlayerMatchView.PresenceView presence(PrivateMatch match, Instant now) {
        return new PlayerMatchView.PresenceView(
                match.connected.contains(PlayerSide.PLAYER_ONE),
                match.connected.contains(PlayerSide.PLAYER_TWO),
                match.disconnectedSince.get(PlayerSide.PLAYER_ONE),
                match.disconnectedSince.get(PlayerSide.PLAYER_TWO),
                cumulativeDisconnected(match, PlayerSide.PLAYER_ONE, now),
                cumulativeDisconnected(match, PlayerSide.PLAYER_TWO, now),
                timing.disconnectGrace().toMillis(),
                match.mode == MatchMode.RANKED
                        ? timing.rankedCumulativeDisconnectLimit().toMillis()
                        : 0,
                now);
    }

    private long cumulativeDisconnected(PrivateMatch match, PlayerSide side, Instant now) {
        long accumulated = match.cumulativeDisconnectedMillis.getOrDefault(side, 0L);
        Instant disconnectedAt = match.disconnectedSince.get(side);
        return disconnectedAt == null
                ? accumulated
                : accumulated + Math.max(0, Duration.between(disconnectedAt, now).toMillis());
    }

    private PlayerMatchView.EventView eventFor(
            PrivateMatch match, PublicMatchEvent event, PlayerSide side, Map<UUID, Piece> piecesById) {
        String outcome = null;
        if (event.type() == PublicMatchEvent.Type.BATTLE_RESOLVED) {
            boolean ownAttacker = event.actor() == side;
            outcome = switch (event.battleOutcome()) {
                case BOTH_REMOVED -> "OWN_PIECE_SPLIT";
                case ATTACKER_SURVIVES -> ownAttacker ? "OWN_PIECE_WON" : "OWN_PIECE_LOST";
                case DEFENDER_SURVIVES -> ownAttacker ? "OWN_PIECE_LOST" : "OWN_PIECE_WON";
            };
        }
        List<UUID> removedIds = event.removedPieceIds().stream()
                .map(id -> idForViewer(match, piecesById.get(id), side))
                .toList();
        return new PlayerMatchView.EventView(
                event.sequence(), event.type(), event.actor(), event.source(), event.destination(),
                removedIds, outcome, event.terminalResult());
    }

    private UUID idForViewer(PrivateMatch match, Piece piece, PlayerSide viewer) {
        if (piece == null) {
            throw new IllegalStateException("Event references an unknown piece");
        }
        return piece.owner() == viewer ? piece.id() : publicId(match, piece.id());
    }

    private UUID publicId(PrivateMatch match, UUID authoritativeId) {
        UUID publicId = match.publicPieceIds.get(authoritativeId);
        if (publicId == null) {
            throw new IllegalStateException("Missing public identifier for piece " + authoritativeId);
        }
        return publicId;
    }
}
