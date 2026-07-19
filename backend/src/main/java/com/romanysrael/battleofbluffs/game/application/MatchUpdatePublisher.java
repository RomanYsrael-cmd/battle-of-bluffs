package com.romanysrael.battleofbluffs.game.application;

import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface MatchUpdatePublisher {
    void publish(MatchUpdate update);

    record MatchUpdate(
            UUID matchId,
            long sequence,
            UpdateType type,
            Map<String, PlayerMatchView> playerViews) {
        public MatchUpdate {
            playerViews = Map.copyOf(playerViews);
        }
    }

    enum UpdateType {
        PLAYER_JOINED,
        FORMATION_SUBMITTED,
        FORMATION_LOCKED,
        MATCH_STARTED,
        MOVE_APPLIED,
        BATTLE_RESOLVED,
        FLAG_CHALLENGE_STARTED,
        TIMER_SYNC,
        PLAYER_CONNECTED,
        PLAYER_DISCONNECTED,
        MATCH_ENDED
    }
}
