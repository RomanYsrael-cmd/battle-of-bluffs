package com.romanysrael.battleofbluffs.game.websocket;

import com.romanysrael.battleofbluffs.game.application.MatchUpdatePublisher.UpdateType;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import java.time.Instant;
import java.util.UUID;

public record MatchUpdateEnvelope(
        UpdateType type,
        UUID matchId,
        long sequence,
        long version,
        Instant serverTimestamp,
        PlayerMatchView view) {
}
