package com.romanysrael.battleofbluffs.game.application;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MatchTimingSettings {
    private final Duration formationLimit;
    private final Duration initialPlayTime;
    private final Duration moveIncrement;
    private final Duration disconnectGrace;
    private final Duration rankedCumulativeDisconnectLimit;

    public MatchTimingSettings(
            @Value("${app.match.formation-limit:5m}") Duration formationLimit,
            @Value("${app.match.initial-clock:15m}") Duration initialPlayTime,
            @Value("${app.match.move-increment:5s}") Duration moveIncrement,
            @Value("${app.match.disconnect-grace:60s}") Duration disconnectGrace,
            @Value("${app.match.ranked-cumulative-disconnect-limit:120s}")
            Duration rankedCumulativeDisconnectLimit) {
        this.formationLimit = positive(formationLimit, "formation limit");
        this.initialPlayTime = positive(initialPlayTime, "initial clock");
        this.moveIncrement = nonNegative(moveIncrement, "move increment");
        this.disconnectGrace = positive(disconnectGrace, "disconnect grace");
        this.rankedCumulativeDisconnectLimit = positive(
                rankedCumulativeDisconnectLimit, "ranked disconnect limit");
    }

    static MatchTimingSettings defaults() {
        return new MatchTimingSettings(
                MatchTimingRules.FORMATION_LIMIT,
                MatchTimingRules.INITIAL_PLAY_TIME,
                MatchTimingRules.MOVE_INCREMENT,
                MatchTimingRules.DISCONNECT_GRACE,
                MatchTimingRules.RANKED_CUMULATIVE_DISCONNECT_LIMIT);
    }

    public Duration formationLimit() {
        return formationLimit;
    }

    public Duration initialPlayTime() {
        return initialPlayTime;
    }

    public Duration moveIncrement() {
        return moveIncrement;
    }

    public Duration disconnectGrace() {
        return disconnectGrace;
    }

    public Duration rankedCumulativeDisconnectLimit() {
        return rankedCumulativeDisconnectLimit;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Match " + name + " must be positive");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException("Match " + name + " cannot be negative");
        }
        return value;
    }
}
