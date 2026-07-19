package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;
import java.util.Optional;

public record TerminalResult(PlayerSide winner, TerminalReason reason) {
    public TerminalResult {
        Objects.requireNonNull(reason, "reason");
        if (winner == null && reason != TerminalReason.THREEFOLD_REPETITION
                && reason != TerminalReason.MOVE_LIMIT
                && reason != TerminalReason.MUTUAL_DRAW
                && reason != TerminalReason.NO_CONTEST
                && reason != TerminalReason.ROOM_CANCELLED) {
            throw new IllegalArgumentException("This terminal reason requires a winner");
        }
    }

    public static TerminalResult win(PlayerSide winner, TerminalReason reason) {
        return new TerminalResult(Objects.requireNonNull(winner, "winner"), reason);
    }

    public static TerminalResult draw(TerminalReason reason) {
        return new TerminalResult(null, reason);
    }

    public Optional<PlayerSide> winningSide() {
        return Optional.ofNullable(winner);
    }
}
