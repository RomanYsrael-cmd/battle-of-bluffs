package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.Board;
import com.romanysrael.battleofbluffs.game.domain.MatchState;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MatchTemporalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchTemporalService.class);

    private final MatchRepository repository;
    private final PlayerMatchViewMapper mapper;
    private Clock clock = Clock.systemUTC();
    private MatchUpdatePublisher updatePublisher = update -> { };
    private MatchTimingSettings timing = MatchTimingSettings.defaults();

    MatchTemporalService(MatchRepository repository, PlayerMatchViewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    void setUpdatePublisher(MatchUpdatePublisher updatePublisher) {
        this.updatePublisher = updatePublisher;
    }

    void setTiming(MatchTimingSettings timing) {
        this.timing = timing;
    }

    void openFormation(PrivateMatch match, Instant now) {
        match.formationDeadline = now.plus(timing.formationLimit());
        for (PlayerSide side : PlayerSide.values()) {
            if (!match.connected.contains(side)) {
                match.disconnectedSince.put(side, now);
            }
            match.cumulativeDisconnectedMillis.putIfAbsent(side, 0L);
        }
    }

    void startPlay(PrivateMatch match, Instant now) {
        match.formationDeadline = null;
        if (match.timerMode != TimerMode.STANDARD_15_PLUS_5) {
            return;
        }
        for (PlayerSide side : PlayerSide.values()) {
            match.remainingMillis.put(side, timing.initialPlayTime().toMillis());
        }
        beginTurn(match, now);
    }

    void finish(
            PrivateMatch match,
            Board board,
            TerminalResult result,
            int acceptedMoveCount) {
        freezeDisconnectedDurations(match, clock.instant());
        match.state = MatchState.terminal(board, result, acceptedMoveCount);
        match.formationDeadline = null;
        match.turnStartedAt = null;
        match.turnDeadline = null;
        match.events.add(new PublicMatchEvent(
                match.nextEventSequence++,
                PublicMatchEvent.Type.MATCH_ENDED,
                result.winner(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                result));
    }

    void ensureNotExpired(PrivateMatch match, Instant receivedAt) {
        if (evaluateDeadlines(match, receivedAt)) {
            throw new MatchApplicationException(
                    MatchErrorCode.TERMINAL_MATCH,
                    "The match reached an authoritative deadline before this command");
        }
    }

    long chargeAcceptedTurn(PrivateMatch match, PlayerSide side, Instant acceptedAt) {
        if (match.timerMode != TimerMode.STANDARD_15_PLUS_5) {
            return 0;
        }
        long stored = match.remainingMillis.getOrDefault(
                side, timing.initialPlayTime().toMillis());
        long elapsed = match.turnStartedAt == null
                ? 0
                : Math.max(0, Duration.between(match.turnStartedAt, acceptedAt).toMillis());
        long remaining = Math.max(0, stored - elapsed);
        match.remainingMillis.put(side, remaining);
        return remaining;
    }

    void freezeActiveClock(PrivateMatch match, Instant now) {
        if (match.state == null
                || match.state.isTerminal()
                || match.timerMode != TimerMode.STANDARD_15_PLUS_5) {
            return;
        }
        chargeAcceptedTurn(match, match.state.currentPlayer().orElseThrow(), now);
    }

    void beginTurn(PrivateMatch match, Instant now) {
        PlayerSide activeSide = match.state.currentPlayer().orElseThrow();
        long remaining = match.remainingMillis.getOrDefault(
                activeSide, timing.initialPlayTime().toMillis());
        match.turnStartedAt = now;
        match.turnDeadline = now.plusMillis(remaining);
    }

    void playerConnected(UUID matchId, String playerId) {
        PrivateMatch match = byId(matchId);
        synchronized (match) {
            PlayerSide side = member(match, playerId);
            Instant now = clock.instant();
            if (match.players.size() < 2) {
                match.connected.add(side);
                return;
            }
            if (evaluateDeadlines(match, now) || terminal(match)) {
                return;
            }
            if (match.connected.contains(side)) {
                return;
            }
            Instant disconnectedAt = match.disconnectedSince.remove(side);
            if (disconnectedAt != null) {
                long elapsed = Math.max(0, Duration.between(disconnectedAt, now).toMillis());
                match.cumulativeDisconnectedMillis.merge(side, elapsed, Long::sum);
            }
            match.connected.add(side);
            persistSystemUpdate(match, MatchUpdatePublisher.UpdateType.PLAYER_CONNECTED);
            evaluateDeadlines(match, now);
        }
    }

    void playerDisconnected(UUID matchId, String playerId) {
        PrivateMatch match = byId(matchId);
        synchronized (match) {
            PlayerSide side = member(match, playerId);
            if (match.players.size() < 2) {
                match.connected.remove(side);
                return;
            }
            Instant now = clock.instant();
            if (evaluateDeadlines(match, now) || terminal(match)) {
                return;
            }
            match.connected.remove(side);
            if (match.disconnectedSince.putIfAbsent(side, now) != null) {
                return;
            }
            persistSystemUpdate(match, MatchUpdatePublisher.UpdateType.PLAYER_DISCONNECTED);
        }
    }

    void evaluateAllDeadlines() {
        Instant now = clock.instant();
        repository.findAll().forEach(match -> {
            synchronized (match) {
                evaluateDeadlines(match, now);
            }
        });
    }

    void publishTimerSyncs() {
        Instant now = clock.instant();
        repository.findAll().forEach(match -> {
            synchronized (match) {
                if (evaluateDeadlines(match, now)
                        || terminal(match)
                        || (match.formationDeadline == null && match.turnDeadline == null)) {
                    return;
                }
                match.liveSequence++;
                repository.save(match);
                publishUpdate(match, MatchUpdatePublisher.UpdateType.TIMER_SYNC);
            }
        });
    }

    void recoverAfterRestart() {
        Instant now = clock.instant();
        repository.findAll().forEach(match -> {
            synchronized (match) {
                if (terminal(match)) {
                    return;
                }
                boolean changed = initializeMissingTiming(match, now);
                if (!match.connected.isEmpty()) {
                    for (PlayerSide side : List.copyOf(match.connected)) {
                        match.disconnectedSince.putIfAbsent(side, now);
                    }
                    match.connected.clear();
                    changed = true;
                }
                if (changed) {
                    match.version++;
                    match.liveSequence++;
                    repository.save(match);
                }
                evaluateDeadlines(match, now);
            }
        });
    }

    boolean evaluateDeadlines(PrivateMatch match, Instant now) {
        if (terminal(match)) {
            return false;
        }
        DeadlineTerminal deadline = earliestDeadline(match);
        if (deadline == null || now.isBefore(deadline.at())) {
            return false;
        }
        if (deadline.result().reason() == TerminalReason.TIMEOUT
                && match.state != null
                && match.state.currentPlayer().isPresent()) {
            match.remainingMillis.put(match.state.currentPlayer().orElseThrow(), 0L);
        } else {
            freezeActiveClock(match, deadline.at());
        }
        Board board = match.state == null
                ? new Board(match.formations.values().stream().flatMap(List::stream).toList())
                : match.state.board();
        int acceptedMoveCount = match.state == null ? 0 : match.state.acceptedMoveCount();
        finish(match, board, deadline.result(), acceptedMoveCount);
        persistSystemUpdate(match, MatchUpdatePublisher.UpdateType.MATCH_ENDED);
        return true;
    }

    private boolean initializeMissingTiming(PrivateMatch match, Instant now) {
        if (match.players.size() < 2) {
            return false;
        }
        boolean changed = false;
        if (match.state == null && match.formationDeadline == null) {
            match.formationDeadline = now.plus(timing.formationLimit());
            changed = true;
        }
        if (match.state != null
                && !match.state.isTerminal()
                && match.timerMode == TimerMode.STANDARD_15_PLUS_5
                && match.turnDeadline == null) {
            for (PlayerSide side : PlayerSide.values()) {
                match.remainingMillis.putIfAbsent(
                        side, timing.initialPlayTime().toMillis());
            }
            beginTurn(match, now);
            changed = true;
        }
        for (PlayerSide side : PlayerSide.values()) {
            match.cumulativeDisconnectedMillis.putIfAbsent(side, 0L);
            if (!match.connected.contains(side) && !match.disconnectedSince.containsKey(side)) {
                match.disconnectedSince.put(side, now);
                changed = true;
            }
        }
        return changed;
    }

    private DeadlineTerminal earliestDeadline(PrivateMatch match) {
        List<DeadlineTerminal> deadlines = new ArrayList<>();
        if (match.formationDeadline != null) {
            TerminalResult result = match.locked.size() == 1
                    ? TerminalResult.win(match.locked.iterator().next(), TerminalReason.SETUP_TIMEOUT)
                    : TerminalResult.draw(TerminalReason.NO_CONTEST);
            deadlines.add(new DeadlineTerminal(match.formationDeadline, result, 3));
        }
        if (match.turnDeadline != null && match.state != null && !match.state.isTerminal()) {
            PlayerSide activeSide = match.state.currentPlayer().orElseThrow();
            deadlines.add(new DeadlineTerminal(
                    match.turnDeadline,
                    TerminalResult.win(activeSide.opponent(), TerminalReason.TIMEOUT),
                    0));
        }
        addDisconnectDeadlines(match, deadlines);
        return deadlines.stream()
                .min(Comparator.comparing(DeadlineTerminal::at)
                        .thenComparingInt(DeadlineTerminal::priority))
                .orElse(null);
    }

    private void addDisconnectDeadlines(
            PrivateMatch match, List<DeadlineTerminal> deadlines) {
        if (match.players.size() < 2 || match.disconnectedSince.isEmpty()) {
            return;
        }
        if (match.mode == MatchMode.RANKED) {
            for (Map.Entry<PlayerSide, Instant> entry : match.disconnectedSince.entrySet()) {
                long accumulated = match.cumulativeDisconnectedMillis.getOrDefault(
                        entry.getKey(), 0L);
                long allowance = Math.max(
                        0,
                        timing.rankedCumulativeDisconnectLimit().toMillis()
                                - accumulated);
                deadlines.add(new DeadlineTerminal(
                        entry.getValue().plusMillis(allowance),
                        TerminalResult.win(
                                entry.getKey().opponent(),
                                TerminalReason.CUMULATIVE_DISCONNECT_FORFEIT),
                        1));
            }
        }
        if (match.disconnectedSince.size() == 2) {
            Instant bothGraceExpiredAt = match.disconnectedSince.values().stream()
                    .map(disconnectedAt -> disconnectedAt.plus(timing.disconnectGrace()))
                    .max(Comparator.naturalOrder())
                    .orElseThrow();
            deadlines.add(new DeadlineTerminal(
                    bothGraceExpiredAt,
                    TerminalResult.draw(TerminalReason.NO_CONTEST),
                    2));
            return;
        }
        match.disconnectedSince.forEach((side, disconnectedAt) -> deadlines.add(
                new DeadlineTerminal(
                        disconnectedAt.plus(timing.disconnectGrace()),
                        TerminalResult.win(side.opponent(), TerminalReason.DISCONNECT_FORFEIT),
                        2)));
    }

    private void freezeDisconnectedDurations(PrivateMatch match, Instant terminalAt) {
        match.disconnectedSince.forEach((side, disconnectedAt) -> {
            long elapsed = Math.max(0, Duration.between(disconnectedAt, terminalAt).toMillis());
            match.cumulativeDisconnectedMillis.merge(side, elapsed, Long::sum);
        });
        match.disconnectedSince.clear();
    }

    private void persistSystemUpdate(
            PrivateMatch match, MatchUpdatePublisher.UpdateType updateType) {
        match.version++;
        match.liveSequence++;
        repository.save(match);
        publishUpdate(match, updateType);
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
                    "Match {} version {} was persisted but its temporal update could not be published",
                    match.id,
                    match.version,
                    exception);
        }
    }

    private PrivateMatch byId(UUID matchId) {
        return repository.findById(matchId).orElseThrow(() -> new MatchApplicationException(
                MatchErrorCode.MATCH_NOT_FOUND, "Match not found"));
    }

    private PlayerSide member(PrivateMatch match, String playerId) {
        PlayerSide side = match.sideOf(playerId);
        if (side == null) {
            throw new MatchApplicationException(
                    MatchErrorCode.PLAYER_NOT_IN_MATCH, "Player does not belong to match");
        }
        return side;
    }

    private boolean terminal(PrivateMatch match) {
        return match.state != null && match.state.isTerminal();
    }

    private record DeadlineTerminal(
            Instant at, TerminalResult result, int priority) {
    }
}
