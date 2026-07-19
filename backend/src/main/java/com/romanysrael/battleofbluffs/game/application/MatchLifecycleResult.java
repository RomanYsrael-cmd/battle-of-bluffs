package com.romanysrael.battleofbluffs.game.application;

import java.util.UUID;

public record MatchLifecycleResult(
        UUID commandId,
        long version,
        UUID matchId,
        Action action) {
    public enum Action {
        ROOM_CANCELLED,
        LOBBY_LEFT
    }
}
