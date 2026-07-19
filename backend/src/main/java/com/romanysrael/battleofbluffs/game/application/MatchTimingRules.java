package com.romanysrael.battleofbluffs.game.application;

import java.time.Duration;

public final class MatchTimingRules {
    public static final Duration FORMATION_LIMIT = Duration.ofMinutes(5);
    public static final Duration INITIAL_PLAY_TIME = Duration.ofMinutes(15);
    public static final Duration MOVE_INCREMENT = Duration.ofSeconds(5);
    public static final Duration DISCONNECT_GRACE = Duration.ofSeconds(60);
    public static final Duration RANKED_CUMULATIVE_DISCONNECT_LIMIT = Duration.ofSeconds(120);

    private MatchTimingRules() {
    }
}
