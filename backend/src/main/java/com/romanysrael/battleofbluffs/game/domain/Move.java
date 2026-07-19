package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;

public record Move(PlayerSide actingPlayer, Position source, Position destination) {
    public Move {
        Objects.requireNonNull(actingPlayer, "actingPlayer");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }
}
