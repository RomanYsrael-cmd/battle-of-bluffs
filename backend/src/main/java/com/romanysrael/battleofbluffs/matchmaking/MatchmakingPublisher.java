package com.romanysrael.battleofbluffs.matchmaking;

import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import java.time.Instant;
import java.util.UUID;

public interface MatchmakingPublisher {
    MatchmakingPublisher NO_OP = (username, event) -> { };

    void found(String username, MatchmakingFound event);

    record MatchmakingFound(
            String type,
            UUID matchId,
            String opponentDisplayName,
            int opponentRating,
            Instant matchedAt,
            PlayerMatchView view) {
        public MatchmakingFound(
                UUID matchId,
                String opponentDisplayName,
                int opponentRating,
                Instant matchedAt,
                PlayerMatchView view) {
            this("MATCHMAKING_FOUND", matchId, opponentDisplayName, opponentRating, matchedAt, view);
        }
    }
}
